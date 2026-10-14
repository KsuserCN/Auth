package cn.ksuser.api.support;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Stateful Redis substitute for rate-limit behavior tests. The script result is simulated;
 * these tests do not execute Lua or claim to verify actual Redis concurrency.
 */
public final class SendCodeRateLimitRedis {
    private record Entry(String value, long expiresAt) {}
    private final Map<String, Entry> entries = new HashMap<>();
    private long now;
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    public SendCodeRateLimitRedis() {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> get(call.getArgument(0)));
        when(values.increment(anyString())).thenAnswer(call -> {
            String key = call.getArgument(0);
            String old = get(key);
            long next = old == null ? 1 : Long.parseLong(old) + 1;
            Entry entry = entries.get(key);
            entries.put(key, new Entry(Long.toString(next), entry == null ? Long.MAX_VALUE : entry.expiresAt()));
            return next;
        });
        doAnswer(call -> {
            put(call.getArgument(0), call.getArgument(1), ((Duration) call.getArgument(2)).toSeconds());
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(redis.hasKey(anyString())).thenAnswer(call -> get(call.getArgument(0)) != null);
        when(redis.delete(anyString())).thenAnswer(call -> entries.remove(call.getArgument(0)) != null);
        when(redis.expire(anyString(), any(Duration.class))).thenAnswer(call -> {
            String key = call.getArgument(0);
            String value = get(key);
            if (value == null) return false;
            put(key, value, ((Duration) call.getArgument(1)).toSeconds());
            return true;
        });
        when(redis.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenAnswer(call -> ttl(call.getArgument(0)));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenAnswer(call -> {
            List<String> keys = call.getArgument(1);
            Object[] arguments = call.getArguments();
            // Mockito expands the Object... argument array when exposing invocations.
            Object[] limits = arguments.length == 3 && arguments[2] instanceof Object[] packed
                ? packed : java.util.Arrays.copyOfRange(arguments, 2, arguments.length);
            if (keys.size() != 4 || limits.length < 6) throw new AssertionError("Unexpected rate-limit script arguments");
            for (int i = 0; i < 4; i++) {
                String key = keys.get(i);
                String count = get(key);
                if (count != null && entries.get(key).expiresAt() == Long.MAX_VALUE) {
                    put(key, count, Long.parseLong(limits[i % 2 == 0 ? 4 : 5].toString()));
                }
            }
            for (int i = 0; i < 4; i++) {
                String count = get(keys.get(i));
                if (count != null && Long.parseLong(count) >= Long.parseLong(limits[i].toString())) return (long) i + 1;
            }
            for (int i = 0; i < 4; i++) {
                String key = keys.get(i);
                String count = get(key);
                long next = count == null ? 1 : Long.parseLong(count) + 1;
                long window = Long.parseLong(limits[i % 2 == 0 ? 4 : 5].toString());
                if (count == null) put(key, Long.toString(next), window);
                else entries.put(key, new Entry(Long.toString(next), entries.get(key).expiresAt()));
            }
            return 0L;
        });
    }

    public StringRedisTemplate template() { return redis; }
    public void advanceSeconds(long seconds) { now += seconds; }
    public void put(String key, String value, long ttlSeconds) { entries.put(key, new Entry(value, now + ttlSeconds)); }
    public void putWithoutExpiry(String key, String value) { entries.put(key, new Entry(value, Long.MAX_VALUE)); }
    public String get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) return null;
        if (entry.expiresAt() <= now) { entries.remove(key); return null; }
        return entry.value();
    }
    public long ttl(String key) {
        if (get(key) == null) return -2;
        if (entries.get(key).expiresAt() == Long.MAX_VALUE) return -1;
        return entries.get(key).expiresAt() - now;
    }
}

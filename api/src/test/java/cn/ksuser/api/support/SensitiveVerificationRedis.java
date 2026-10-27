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

/** Local Redis state for verification lifecycle tests; no network or wall-clock sleeps. */
public final class SensitiveVerificationRedis {
    private record Entry(String value, long expiresAtSeconds) {}

    private final StringRedisTemplate template = mock(StringRedisTemplate.class);
    private final Map<String, Entry> entries = new HashMap<>();
    private long nowSeconds;

    @SuppressWarnings("unchecked")
    public SensitiveVerificationRedis() {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> {
            Entry entry = liveEntry(call.getArgument(0));
            return entry == null ? null : entry.value();
        });
        doAnswer(call -> {
            TimeUnit unit = call.getArgument(3);
            entries.put(call.getArgument(0), new Entry(call.getArgument(1),
                nowSeconds + unit.toSeconds(call.getArgument(2, Long.class))));
            return null;
        }).when(values).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        doAnswer(call -> {
            Duration duration = call.getArgument(2);
            entries.put(call.getArgument(0), new Entry(call.getArgument(1), nowSeconds + duration.toSeconds()));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(template.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenAnswer(call -> {
            Entry entry = liveEntry(call.getArgument(0));
            return entry == null ? -2L : entry.expiresAtSeconds() - nowSeconds;
        });
        when(template.delete(anyString())).thenAnswer(call -> entries.remove(call.getArgument(0)) != null);
        when(values.increment(anyString())).thenAnswer(call -> {
            String key = call.getArgument(0);
            Entry old = liveEntry(key);
            long value = old == null ? 1 : Long.parseLong(old.value()) + 1;
            entries.put(key, new Entry(Long.toString(value), Long.MAX_VALUE));
            return value;
        });
        when(template.execute(any(RedisScript.class), anyList(), anyString())).thenAnswer(call -> {
            List<String> keys = call.getArgument(1);
            String key = keys.getFirst();
            Entry entry = liveEntry(key);
            if (entry == null) {
                return -1L;
            }
            if (!entry.value().equals(call.getArgument(2))) {
                return 0L;
            }
            entries.remove(key);
            return 1L;
        });
    }

    public StringRedisTemplate template() {
        return template;
    }

    public void advanceSeconds(long seconds) {
        nowSeconds += seconds;
    }

    private Entry liveEntry(String key) {
        Entry entry = entries.get(key);
        if (entry != null && entry.expiresAtSeconds() <= nowSeconds) {
            entries.remove(key);
            return null;
        }
        return entry;
    }
}

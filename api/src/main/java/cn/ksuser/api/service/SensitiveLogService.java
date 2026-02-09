package cn.ksuser.api.service;

import cn.ksuser.api.dto.PageResponse;
import cn.ksuser.api.dto.SensitiveLogQueryRequest;
import cn.ksuser.api.dto.SensitiveLogResponse;
import cn.ksuser.api.entity.UserSensitiveLog;
import cn.ksuser.api.repository.UserSensitiveLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SensitiveLogService {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveLogService.class);

    @Autowired
    private UserSensitiveLogRepository logRepository;

    @Autowired
    private IpLocationService ipLocationService;

    @Autowired
    private UserAgentParserService userAgentParserService;

    @Autowired
    private RiskScoringService riskScoringService;

    @Autowired
    private UserService userService;

    /**
     * 异步记录敏感操作日志
     */
    @Async
    public void logAsync(UserSensitiveLog log) {
        try {
            logger.debug("logAsync called: userId={}, operation={}, result={}", 
                        log.getUserId(), log.getOperationType(), log.getResult());

            // 异步获取IP属地
            if (log.getIpLocation() == null && log.getIpAddress() != null) {
                String location = ipLocationService.getIpLocation(log.getIpAddress());
                log.setIpLocation(location);
                logger.debug("IP location resolved: ip={}, location={}", log.getIpAddress(), location);
            }

            // 解析User-Agent
            if (log.getUserAgent() != null && log.getBrowser() == null) {
                UserAgentParserService.UserAgentInfo uaInfo = userAgentParserService.parse(log.getUserAgent());
                log.setBrowser(uaInfo.getBrowser());
                log.setDeviceType(uaInfo.getDeviceType());
                logger.debug("User-Agent parsed: browser={}, device={}, ua={}", 
                            uaInfo.getBrowser(), uaInfo.getDeviceType(), log.getUserAgent());
            }

            // 确保创建时间存在
            if (log.getCreatedAt() == null) {
                log.setCreatedAt(LocalDateTime.now());
            }

            // 计算风险评分（在补全IP/设备/浏览器后）
            try {
                if (log.getUserId() != null) {
                    var user = userService.findById(log.getUserId()).orElse(null);
                    Integer riskScore = riskScoringService.calculateRiskScore(log, user);
                    log.setRiskScore(riskScore);
                    logger.debug("Risk score calculated: userId={}, score={}", log.getUserId(), riskScore);
                } else if (log.getRiskScore() == null) {
                    log.setRiskScore(0);
                }
            } catch (Exception e) {
                logger.warn("Failed to calculate risk score", e);
                if (log.getRiskScore() == null) {
                    log.setRiskScore(0);
                }
            }

            logRepository.save(log);
            logger.debug("Sensitive operation log saved: userId={}, operation={}", 
                         log.getUserId(), log.getOperationType());
        } catch (Exception e) {
            logger.error("Failed to save sensitive operation log", e);
        }
    }

    /**
     * 同步记录敏感操作日志（当需要立即记录时使用）
     */
    public void logSync(UserSensitiveLog log) {
        try {
            logger.debug("logSync called: userId={}, operation={}, result={}", 
                        log.getUserId(), log.getOperationType(), log.getResult());

            // 同步获取IP属地
            if (log.getIpLocation() == null && log.getIpAddress() != null) {
                String location = ipLocationService.getIpLocation(log.getIpAddress());
                log.setIpLocation(location);
                logger.debug("IP location resolved: ip={}, location={}", log.getIpAddress(), location);
            }

            // 解析User-Agent
            if (log.getUserAgent() != null && log.getBrowser() == null) {
                UserAgentParserService.UserAgentInfo uaInfo = userAgentParserService.parse(log.getUserAgent());
                log.setBrowser(uaInfo.getBrowser());
                log.setDeviceType(uaInfo.getDeviceType());
                logger.debug("User-Agent parsed: browser={}, device={}, ua={}", 
                            uaInfo.getBrowser(), uaInfo.getDeviceType(), log.getUserAgent());
            }

            // 确保创建时间存在
            if (log.getCreatedAt() == null) {
                log.setCreatedAt(LocalDateTime.now());
            }

            // 计算风险评分（在补全IP/设备/浏览器后）
            try {
                if (log.getUserId() != null) {
                    var user = userService.findById(log.getUserId()).orElse(null);
                    Integer riskScore = riskScoringService.calculateRiskScore(log, user);
                    log.setRiskScore(riskScore);
                    logger.debug("Risk score calculated: userId={}, score={}", log.getUserId(), riskScore);
                } else if (log.getRiskScore() == null) {
                    log.setRiskScore(0);
                }
            } catch (Exception e) {
                logger.warn("Failed to calculate risk score", e);
                if (log.getRiskScore() == null) {
                    log.setRiskScore(0);
                }
            }

            logRepository.save(log);
        } catch (Exception e) {
            logger.error("Failed to save sensitive operation log", e);
            throw e;
        }
    }

    /**
     * 查询用户的敏感操作日志
     */
    public PageResponse<SensitiveLogResponse> queryLogs(Long userId, SensitiveLogQueryRequest request) {
        // 参数验证
        int page = request.getPage() != null && request.getPage() > 0 ? request.getPage() : 1;
        int pageSize = request.getPageSize() != null && request.getPageSize() > 0 && request.getPageSize() <= 100 
                       ? request.getPageSize() : 20;

        Pageable pageable = PageRequest.of(page - 1, pageSize);

        // 日期转换
        LocalDateTime startDate = request.getStartDate() != null 
                                  ? LocalDateTime.of(request.getStartDate(), LocalTime.MIN)
                                  : null;
        LocalDateTime endDate = request.getEndDate() != null 
                                ? LocalDateTime.of(request.getEndDate(), LocalTime.MAX)
                                : null;

        // 结果枚举转换
        UserSensitiveLog.OperationResult resultEnum = null;
        if (request.getResult() != null && !request.getResult().isEmpty()) {
            try {
                resultEnum = UserSensitiveLog.OperationResult.valueOf(request.getResult().toUpperCase());
            } catch (IllegalArgumentException e) {
                // 忽略无效的结果值
            }
        }

        // 查询
        Page<UserSensitiveLog> logPage = logRepository.findByUserIdWithFilters(
                userId,
                startDate,
                endDate,
                request.getOperationType(),
                resultEnum,
                pageable
        );

        // 转换为DTO
        List<SensitiveLogResponse> responseList = logPage.getContent().stream()
                .map(SensitiveLogResponse::new)
                .collect(Collectors.toList());

        return new PageResponse<>(responseList, page, pageSize, logPage.getTotalElements());
    }
}

package com.vulnark.service;

import com.vulnark.entity.SystemConfig;
import com.vulnark.repository.SystemConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * 系统配置服务：KV 配置读写 + SMTP 测试发送
 */
@Service
public class SystemConfigService {

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    /**
     * 全部配置，按 category 分组
     */
    public Map<String, Map<String, String>> getAllGrouped() {
        List<SystemConfig> all = systemConfigRepository.findAllByOrderByCategoryAscConfigKeyAsc();
        return all.stream().collect(Collectors.groupingBy(
                SystemConfig::getCategory,
                Collectors.toMap(SystemConfig::getConfigKey,
                        c -> c.getConfigValue() == null ? "" : c.getConfigValue(),
                        (a, b) -> b)));
    }

    /**
     * 批量保存配置（存在则更新值，不存在则忽略——配置项由迁移脚本定义）
     */
    @Transactional
    public void saveBatch(Map<String, String> configs) {
        configs.forEach((key, value) -> systemConfigRepository.findByConfigKey(key).ifPresent(cfg -> {
            cfg.setConfigValue(value == null ? "" : value);
            systemConfigRepository.save(cfg);
        }));
    }

    /**
     * 用给定 SMTP 配置发送测试邮件（不落库，验证配置可用性）
     */
    public void sendTestMail(Map<String, String> smtp, String to) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.getOrDefault("smtp.host", ""));
        sender.setPort(Integer.parseInt(smtp.getOrDefault("smtp.port", "587")));
        sender.setUsername(smtp.getOrDefault("smtp.username", ""));
        sender.setPassword(smtp.getOrDefault("smtp.password", ""));
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        boolean ssl = Boolean.parseBoolean(smtp.getOrDefault("smtp.ssl", "true"));
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", String.valueOf(ssl));
        props.put("mail.smtp.starttls.enable", String.valueOf(!ssl));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(smtp.getOrDefault("smtp.from", smtp.get("smtp.username")));
        message.setTo(to);
        message.setSubject("VulnArk 邮件配置测试");
        message.setText("这是一封 VulnArk 系统设置的测试邮件。\n\n如果您收到此邮件，说明 SMTP 配置有效。");
        sender.send(message);
    }
}

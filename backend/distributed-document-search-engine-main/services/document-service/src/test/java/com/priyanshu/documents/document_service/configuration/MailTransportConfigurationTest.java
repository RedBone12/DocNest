package com.priyanshu.documents.document_service.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class MailTransportConfigurationTest {
    @Test
    void mailConfigurationSupportsImplicitTlsWithoutDisablingCertificateChecks() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        var properties = yaml.getObject();
        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.mail.properties.mail.smtp.ssl.enable")).isEqualTo("${SMTP_SSL:false}");
        assertThat(properties.getProperty("spring.mail.properties.mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
        assertThat(properties.getProperty("spring.mail.properties.mail.smtp.starttls.enable")).isEqualTo("${SMTP_STARTTLS:true}");
        assertThat(properties.getProperty("spring.mail.properties.mail.smtp.starttls.required")).isEqualTo("${SMTP_STARTTLS:true}");
        assertThat(properties.getProperty("spring.mail.properties.mail.smtp.ssl.trust")).isNull();
    }
}

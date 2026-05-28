package ai.platon.pulsar.boot.autoconfigure

import ai.platon.pulsar.core.api.PulsarSession
import ai.platon.pulsar.ql.context.SQLContext
import ai.platon.pulsar.ql.context.SQLContexts
import ai.platon.pulsar.skeleton.context.support.AbstractPulsarContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Scope
import org.springframework.beans.factory.config.ConfigurableBeanFactory

@Configuration(proxyBeanMethods = false)
@Import(PulsarBeansAutoConfiguration::class)
class PulsarContextConfiguration(
    val applicationContext: ApplicationContext
) {
    @Bean
    @ConditionalOnMissingBean(name = ["pulsarContext"])
    fun pulsarContext(): SQLContext {
        val context = SQLContexts.create(applicationContext)
        require(context is AbstractPulsarContext)
        require(context.applicationContext == applicationContext)
        return context
    }

    @Bean(name = ["getPulsarSession"])
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @ConditionalOnMissingBean(name = ["getPulsarSession"])
    fun getPulsarSession(pulsarContext: SQLContext): PulsarSession {
        return pulsarContext.createSession()
    }
}

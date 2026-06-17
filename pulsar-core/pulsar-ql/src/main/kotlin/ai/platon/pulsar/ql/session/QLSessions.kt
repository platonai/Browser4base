package ai.platon.pulsar.ql.session

import ai.platon.pulsar.common.config.VolatileConfig
import ai.platon.pulsar.common.getLogger
import ai.platon.pulsar.ql.context.GenericQLContext
import ai.platon.pulsar.ql.context.StaticQLContext
import ai.platon.pulsar.skeleton.context.support.AbstractPulsarContext
import ai.platon.pulsar.skeleton.session.AbstractPulsarSession

open class BasicSQLSession(
    context: AbstractPulsarContext,
    sessionConfig: VolatileConfig,
    id: Long = nextId()
) : AbstractPulsarSession(context, sessionConfig, id) {
    private val logger = getLogger(GenericSQLSession::class)
}

open class GenericSQLSession(
    context: GenericQLContext,
    sessionConfig: VolatileConfig,
    id: Long = nextId()
) : AbstractPulsarSession(context, sessionConfig, id) {
    private val logger = getLogger(GenericSQLSession::class)
}

class StaticSQLSession(
    context: StaticQLContext,
    sessionConfig: VolatileConfig,
) : GenericSQLSession(context, sessionConfig) {

}

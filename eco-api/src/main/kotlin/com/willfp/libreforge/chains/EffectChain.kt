package com.willfp.libreforge.chains

import com.willfp.libreforge.effects.ConfiguredEffect
import com.willfp.libreforge.triggers.InvocationData

class EffectChain(
    val id: String,
    private val components: Iterable<ChainComponent>
) {
    operator fun invoke(invocationData: InvocationData) {
        for (component in components) {
            component(invocationData)
        }
    }
}

interface ChainComponent {
    operator fun invoke(data: InvocationData)
}

class ChainComponentEffect(
    val effect: ConfiguredEffect
) : ChainComponent {
    override fun invoke(data: InvocationData) {
        effect(data, ignoreTriggerList = true)
    }
}

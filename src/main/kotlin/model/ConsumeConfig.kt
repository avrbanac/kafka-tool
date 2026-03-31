package model

sealed class OffsetStrategy {
    object Earliest : OffsetStrategy()
    object Latest : OffsetStrategy()
    object LastMessage : OffsetStrategy()
    data class SpecificOffset(val offset: Long) : OffsetStrategy()

    fun displayName(): String = when (this) {
        is Earliest -> "From Beginning"
        is Latest -> "Latest"
        is LastMessage -> "Last Message"
        is SpecificOffset -> "Specific Offset"
    }
}

data class ConsumeConfig(
    val offsetStrategy: OffsetStrategy = OffsetStrategy.Latest,
    val maxMessages: Int? = 100
)

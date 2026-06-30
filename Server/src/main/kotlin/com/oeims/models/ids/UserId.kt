package com.oeims.models.ids

import java.util.*

@JvmInline
value class UserId(val value: UUID) {
    // TODO: More validation? Different exceptions?
}

fun UUID.toUserId() = UserId(this)

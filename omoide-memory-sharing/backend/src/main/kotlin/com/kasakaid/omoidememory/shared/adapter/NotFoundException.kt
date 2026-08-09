package com.kasakaid.omoidememory.shared.adapter

class NotFoundException(
    message: String = "Requested resource was not found.",
) : RuntimeException(message)

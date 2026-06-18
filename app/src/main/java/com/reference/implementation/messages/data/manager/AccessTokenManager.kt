package com.reference.implementation.messages.data.manager

import android.content.Context

class AccessTokenManager(
    context: Context,
    override val keyAlias: String,
    override val encryptedTokenKey: String,
    override val initializationVectorKey: String
): AbstractTokenManager(context)
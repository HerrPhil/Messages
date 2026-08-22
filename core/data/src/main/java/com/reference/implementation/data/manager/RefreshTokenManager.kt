package com.reference.implementation.data.manager

import android.content.Context

class RefreshTokenManager(
    context: Context,
    override val keyAlias: String,
    override val encryptedTokenKey: String,
    override val initializationVectorKey: String
): AbstractTokenManager(context)
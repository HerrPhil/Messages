package com.reference.implementation.messages

import android.app.Application
import com.reference.implementation.messages.data.repository.AppContainer
import com.reference.implementation.messages.data.repository.AppMessageContainer

class MessageApplication : Application() {

    // I am going to re-factor this so that implementation of an app container has the logic
    // provide data from repository instances.
    // The implementation of repository interfaces have constructors that take an api service.
    // The app container will return use cases.
    // The use cases have constructors that take a repository.
    // See the AppViewModelProvider for the integration of view model and use case

    /**
     * AppContainer instance used  by the rest of the classes to obtain dependencies
     */
    lateinit var container: AppContainer

    /**
     * The application shall create the AppContainer when the app is created
     */
    override fun onCreate() {
        super.onCreate()
        container = AppMessageContainer(this)
    }
}
# simple_idling_resource_android

A thread named "idlingMonitor" will keep checking whether any other background thread is running for every 20ms and notify EspressoIdlingResource.
Pros of this technique:  
* Fully decoupled from development code. No single line was written in development package. Easy to integrate in any app.
* Easy to maintain, less bugs
Concerns: (No need to worry about that)
* I think the technique is fragile. So far i know, no one else is using this technique

Add (implementation 'org.apache.commons:commons-lang3:3.11') before copy pasting it.

Issues in other idling resource techhniques:

* Background work will be done using RxJava, Coroutines, usual thread poll executor, asynctask, etc..
It's so tricky to do an implementation covering all these cases.
Ex: https://github.com/Kotlin/kotlinx.coroutines/issues/242
You can see lots of discussion going on since 2018 for idling resource implementation in coroutines.

* It pollutes the development code

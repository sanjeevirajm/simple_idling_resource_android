# simple_idling_resource_android
Implementing idling resource requires so much changes in development code and it pollutes development code. So i created this one to address it.

A thread named "idlingMonitor" will keep checking whether any other background thread is running for every 20ms and notify EspressoIdlingResource.
Pros of this technique:  
* Fully decoupled from development code. No single line was written in development package. Easy to integrate in any app.
* Easy to maintain, less bugs

Concerns:
* It's fragile

Add (androidTestImplementation 'org.apache.commons:commons-lang3:3.11') before copy pasting it.
Paste the code in test folder

Issues in other idling resource techhniques:

* Background work will be done using RxJava, Coroutines, usual thread poll executor, asynctask, etc..
It's so tricky to do an implementation covering all these cases.
Ex: https://github.com/Kotlin/kotlinx.coroutines/issues/242
You can see lots of discussion going on since 2018 for idling resource implementation in coroutines.

* It pollutes the development code

* It is shipped in production build

Issue tracker link - Feature request
https://issuetracker.google.com/issues/193815949

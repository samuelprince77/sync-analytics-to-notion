# Sync analytics to notion 
This is a gradle plugin that registers tasks that allow you to seamlessly sync all your in app analytics events to a 
database in notion where these events can be viewed in a human friendly way. 

This plugin aims to be both easy to use and flexible so it provides a default kotlin code parser while also giving you 
the ability to pass your own parser.

To be able to use this plugin, you first need to create a notion [integration](#https://www.notion.so/integrations) and 
add it to the notion database that you would like to save the events to.

## Usage
Apply the plugin to your module and provide the following:
- `notion auth key` 
- `notion database Id` 
- `notion event name column name`
- `notion event parameters column name`

Example in your `build.gradle.kts` file:
```kotlin
plugins {
    id("sync-analytics-to-notion")
}

syncAnalyticsToNotion { 
    setNotionAuthKey("YOUR_NOTION_AUTH_KEY")
    // this is the database where the events found will be posted to
    setNotionDatabaseId("YOUR_NOTION_DATABASE_ID")
    // The column names you specify here should exactly the match their respective names in your notion database
    setNotionEventNameColumnName("YOUR_EVENT_NAME_COLUMN_NAME")
    setNotionParametersColumnName("YOUR_EVENT_PARAMETERS_COLUMN NAME")
}
```

A task will be created called `syncAnalyticsToNotion` that you can invoke directly like this:
`./gradlew syncAnalyticsToNotion`

By default, this plugin provides you with a kotlin code parser that will look for the analytics events but it expects a
strict format as follows:
- A function must exist that logs your analytics events and parameters. This function must be annotated with 
`@AnalyticsEventLogger` that comes bundled with this plugin. This function must have two parameters, the first one 
being a string which represents the event name and the second one being a [Bundle](#https://developer.android.com/reference/android/os/Bundle)

Example in your `.kt` file:
```kotlin
@AnalyticsEventLogger
private fun sampleEventLogger(eventName: String, bundle: Bundle) {
    // ...
}
```
- Usages of this annotated function should preserve the order of parameters as shown above. Additionally, within the body
of the function's usages, only the following functions will be searched for and parsed:
  - `putString`
  - `putInt`
  - `putDouble`
  - `putLong`

Example in your `.kt` file:
```kotlin
// Top level constants within the same module will be able to be parsed and their actual value exracted
private const val SOME_PARAM_NAME = "example_reference"

fun example(
    example1: String,
    example2: Int,
    example3: Double,
    example4: Long,
) {
    sampleEventLogger(
        eventName = "your_event_name", 
        Bundle().apply {
            putString(SOME_PARAM_NAME, example1)
            putString("example_string", example1)
            putInt("example_int", example2)
            putDouble("example_double", example3)
            putLong("example_long", example4)
        }
    )
}

fun someOtherExample(
    example: String
) {
    sampleEventLogger(
        eventName = "your_other_event_name",
        Bundle().apply {
            putString("another_param_name", example)
        }
    )
}

fun exampleWithNoParameters() {
    sampleEventLogger(
        eventName = "your_no_params_event_name",
        Bundle()
    )
}
```

Based off this example, the plugin will produce the following result in notion:

| YOUR_EVENT_NAME_COLUMN_NAME | YOUR_EVENT_PARAMETERS_COLUMN NAME                                                                       |
|-----------------------------|---------------------------------------------------------------------------------------------------------|
| your_event_name             | - example_reference<br/> - example_string<br/> - example_int<br/> - example_double<br/> - example_long  |
| your_other_event_name       | another_param_name                                                                                      |
| your_no_params_event_name   | your_no_params_event_name                                                                               |

This plugin will also take care of removing (archiving) any events from notion that were not found in the code making
sure your code is the single source of truth.

In case this setup is too strict for your liking, you can pass your own parser that should be an implementation of 
`AnalyticsEventInfoParser`. This interface provides you with all the kotlin files contained in your module's main source
set.

Example in your `build.gradle.kts` file:
```kotlin
object ExampleCustomParser: AnalyticsEventInfoParser {
    // This provides you with all the kotlin files contained within the main source set of your module that you can 
    // parse manually
    override fun parseKtFiles(ktFiles: List<KtFile>): List<AnalyticsEventInfo> {
        TODO("Add your implementation here")
    }
}

syncAnalyticsToNotion { 
    // ...
    setAnalyticsEventInfoParser(ExampleCustomParser)
    // ..
}
```

## License
```text
Copyright 2023 Samuel Prince.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
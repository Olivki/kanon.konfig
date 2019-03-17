## kanon.konfig

A "configuration" library/framework for Kotlin designed to be "user-first" rather than "dev-first".

This library is partially inspired from the great [konf](https://github.com/uchuhimo/konf) library, reason for making this library is because while konf is great, it's very heavily catered towards being "dev-first", and what I needed was something that was catered towards "user-first", that is, producing a configuration file which you know will be edited by a human manually, and for that konf does not really do the job very nicely. My other "problem" with konf is that essentially the only way to create `Items` is through property delegation, which can get very ugly and cumbersome if you're trying to create a configuration for a structure where you *won't* always have access to a `object` reference of it. And thus this library was born.

kanon.konfig is mainly made just for personal use, it is however thoroughly documented, in case someone else would have any use for it. As for actual non-kotlindoc documentation, there is none, and most likely won't be any, but the kotlindocs should be more than enough to give anyone an understanding of how to use this library.

**NOTE**: kanon.konfig will most likely *not* work very well if used from Java, as it *heavily* relies on Kotlins `reified` generics and the `inline` mechanic and its smart casting, while it is not impossible to use this library from the Java side, the experience will most likely not be very good, and certain parts of the library have been designed to *deliberately* not work when used from Java, due to several reasons.

## Installation

Gradle

- Groovy

  ```groovy
  repositories {
      maven { url "https://dl.bintray.com/olivki/kanon" }
  }
  
  dependencies {
      compile "moe.kanon.konfig:kanon.konfig:1.0.0"
  }
  ```

- Kotlin

  ```kotlin
  repositories {
      maven(url = "https://dl.bintray.com/olivki/kanon")
  }
  
  dependencies {
      compile(group = "moe.kanon.konfig", name = "kanon.konfig", version = "1.0.0")
  }
  ```

Maven

```xml
<dependency>
    <groupId>moe.kanon.konfig</groupId>
    <artifactId>kanon.konfig</artifactId>
    <version>1.0.0</version>
    <type>pom</type>
</dependency>

```

## License

````
Copyright 2019 Oliver Berg

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
````
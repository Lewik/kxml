# kxml

This project **is not abandonned**.

[![](https://jitpack.io/v/lewik/kxml.svg)](https://jitpack.io/#lewik/kxml)

Kxml is simple xml parser
                                      
                                      
## Download
Use https://jitpack.io repository
```
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```
Use these dependencies per kotlin module respectively:
```
compile 'com.github.lewik.kxml:kxml-metadata:0.0.4' //for common modules
compile 'com.github.lewik.kxml:kxml-js:0.0.4'  //for js modules
compile 'com.github.lewik.kxml:kxml-jvm:0.0.4'  //for jvm modules
```

## Usage                                              
```kotlin
fun main(){
    Kxml.parse(text)
}
```

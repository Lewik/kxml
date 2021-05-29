# kxml

This project **is not abandonned**.

[![](https://jitpack.io/v/lewik/kxml.svg)](https://jitpack.io/#lewik/kxml)

Kxml is a simple XML parser.  
No CDATA, comments and no ampersands yet.
                                      
                                      
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
api("com.github.lewik.kxml:kxml:0.0.6")
```

## Usage                                              
```kotlin
fun main(){
    Kxml.parse(text)
}
```

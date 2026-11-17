package com.example.ios

interface Observer<T> { fun onChanged(item: T) } // no-throw: callback into Swift

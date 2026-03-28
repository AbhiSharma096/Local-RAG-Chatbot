package com.example.local_rag.data

import android.content.Context
import io.objectbox.BoxStore

object ObjectBox {
    lateinit var store: BoxStore
        private set
    
    fun init(context: Context) {
        // MyObjectBox is generated automatically by ObjectBox plugin during build
        store = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .name("localrag-db")
            .build()
    }
}

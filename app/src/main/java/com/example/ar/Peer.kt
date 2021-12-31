package com.example.ar

import java.nio.ByteBuffer

/**
 * Created by wyylling@gmail.com on 03/01/2018.
 */
class Peer {
    var uid = 0
    var data: ByteBuffer? = null
    var width = 0
    var height = 0
    var rotation = 0
    var ts: Long = 0
}
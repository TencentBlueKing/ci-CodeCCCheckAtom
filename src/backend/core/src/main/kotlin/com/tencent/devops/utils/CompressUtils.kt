package com.tencent.devops.utils

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

object CompressUtils {

    fun zlibCompress(data: ByteArray): ByteArray {
        val deflater = Deflater()
        try {
            deflater.reset()
            deflater.setInput(data)
            deflater.finish()
            val buf = ByteArray(8192)
            ByteArrayOutputStream(data.size).use { bos ->
                while (!deflater.finished()) {
                    val size = deflater.deflate(buf)
                    bos.write(buf, 0, size)
                }
                return bos.toByteArray()
            }
        } finally {
            deflater.end()
        }
    }

    fun decompress(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.reset()
        inflater.setInput(data)

        try {
            val buf = ByteArray(8192)
            ByteArrayOutputStream(data.size).use { bos ->
                while (!inflater.finished()) {
                    val size = inflater.inflate(buf)
                    bos.write(buf, 0, size)
                }
                return bos.toByteArray()
            }
        } finally {
            inflater.end()
        }
    }

}

//  fun main(args: Array<String>) {
//      val str = "eJztlk1LG0EYx+9+iiVnmzU1W43XNNBQui1hPUlZJrOPyZDZmWV2o6YieLAmUEFb1KQv2tIXCgXTQ2kPbcFPM2v2W3RfEmxqpYo9SMhtnv8889+Z58cfdkFZnVCUFLFSypySyuW07HRqMlI85NaKsToV17gKuAZCRzbErfN6US8aZt64X0oOuB7y6m60l4nrRULhAfKqbHBCtZCHVIqYVWfqMhc110EYVErKrsCqI7jHMaeqC2IJhBnXKmEsXGNu25ylnXIa4+RjFnEdihp55EGFi0bs77/6JFsH8uMTubV/8mLDb+3Ip52hdqPhwF9b3zSD5rY8avvdr/3HQHgH4sXGN2OFEgZ63S6DOJ0JqTAuoATI5Wzg3Z+WgPBqBkmentFymdlpLTs1k9WS4QhwEBGD/d/4njI84gSqPNrToFU8AyEpYZmlZA7WdzSPxHUDcfy6OOckMJDg79L3u97nEY2ovB3WoGz1q9zz/k9v5ZuJlRgHtrHNcRi2tuTHTEiM5eJ6LJ367gFMZYL4NVefgLdScxxA=="
//      println(String(CompressUtils.decompress(Base64.getDecoder().decode(str.toByteArray()))))
//  }

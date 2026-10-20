package com.sabdevz.widget

import com.sabdevz.widget.Node
import com.sabdevz.widget.Packet
import kotlin.random.Random

class Connection(
    val from: Node,
    val to: Node
) {

    val packets = ArrayList<Packet>()

    init {
        repeat(4) {
            packets.add(
                Packet(
                    Random.nextFloat(),
                    0.002f + Random.nextFloat() * 0.003f
                )
            )
        }
    }

}
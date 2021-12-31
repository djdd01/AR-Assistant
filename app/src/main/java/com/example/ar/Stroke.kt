/*
 * Copyright 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.ar

import com.example.ar.ExtrudedCylinder.makeExtrudedCylinder
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Material
import com.google.ar.sceneform.rendering.ModelRenderable

/** Collects points to be drawn  */
class Stroke(private val anchorNode: AnchorNode, private val material: Material?) {
    private val node = Node()
    private val lineSimplifier = LineSimplifier()
    private var shape: ModelRenderable? = null
    fun add(pointInWorld: Vector3?) {
        val pointInLocal = anchorNode.worldToLocalPoint(pointInWorld)
        val points = lineSimplifier.points
        if (numOfPoints < 1) {
            lineSimplifier.add(pointInLocal)
            return
        }
        val prev = points[points.size - 1]
        val diff = Vector3.subtract(prev, pointInLocal)
        if (diff.length() < MINIMUM_DISTANCE_BETWEEN_POINTS) {
            return
        }
        lineSimplifier.add(pointInLocal)
        val renderableDefinition = makeExtrudedCylinder(CYLINDER_RADIUS, points, material)
        if (shape == null) {
            shape = ModelRenderable.builder().setSource(renderableDefinition).build().join()
            node.renderable = shape
        } else {
            shape!!.updateFromDefinition(renderableDefinition)
        }
    }

    fun clear() {
        lineSimplifier.points.clear()
        node.setParent(null)
    }

    private val numOfPoints: Int
        get() = lineSimplifier.points.size

    override fun toString(): String {
        var result = "Vector3[] strokePoints = {"
        for (vector3 in lineSimplifier.points) {
            result += """new Vector3(${vector3!!.x}f, ${vector3.y}f, ${vector3.z}f),
 """
        }
        return result.substring(0, result.length - 3) + "};"
    }

    companion object {
        private const val CYLINDER_RADIUS = 0.005f
        private const val MINIMUM_DISTANCE_BETWEEN_POINTS = 0.005f
    }

    init {
        node.setParent(anchorNode)
    }
}
/*
 * Copyright University of Basel, Graphics and Vision Research Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package faces.utils

import java.io.{File, FileInputStream, FileOutputStream}

import scalismo.faces.color.{ColorSpaceOperations, RGBA}
import scalismo.faces.image.BufferedImageConverter
import scalismo.faces.io.{MeshIO, PixelImageIO}
import scalismo.faces.mesh.{ColorNormalMesh3D, TextureMappedProperty}
import scalismo.geometry.{Point, _2D}
import scalismo.mesh.{MeshSurfaceProperty, TriangleCell, TriangleList}
import spray.json.JsObject

import scala.reflect.ClassTag
import scala.util.Try
import spray.json._

object TextureMappedPropertyIO extends App {

  import scalismo.faces.io.renderparameters.RenderParameterJSONFormatV2._

  import scalismo.faces.io.RenderParameterIO._
  def read[A: ClassTag](directory: String, stem: String)(implicit converter: BufferedImageConverter[A], ops: ColorSpaceOperations[A]): TextureMappedProperty[A] = read[A](new File(directory+"/"+stem+".json"),new File(directory+"/"+stem+".png"))

  def read[A: ClassTag](mappingFile: File, imageFile: File)(implicit converter: BufferedImageConverter[A],  ops: ColorSpaceOperations[A]) : TextureMappedProperty[A] = {

    import scalismo.faces.io.RenderParameterIO.readASTFromStream

    val fields = readASTFromStream(new FileInputStream(mappingFile)).asJsObject.fields
    val triangles = fields("triangles").convertTo[IndexedSeq[TriangleCell]]
    val triangulation = TriangleList(triangles)

    val textureMapping = fields("textureMapping").convertTo[MeshSurfaceProperty[Point[_2D]]]

    val texture = PixelImageIO.read[A](imageFile).get

    TextureMappedProperty[A](triangulation, textureMapping, texture)
  }

  def write[A:ClassTag](textureMappedProperty: TextureMappedProperty[A], directory: String, stem: String)(implicit converter: BufferedImageConverter[A]): Try[Unit] = Try {
    val writeImage = PixelImageIO.write(
      textureMappedProperty.texture,
      new File(directory+"/"+stem+".png")
    ).get

    val mapping = JsObject(
      "triangles" -> textureMappedProperty.triangulation.triangles.toJson,
      "textureMapping" -> textureMappedProperty.textureMapping.toJson,
      "@type" -> "TextureMappedProperty".toJson
    )

    val os = new FileOutputStream(new File(directory+"/"+stem+".json"))
    writeASTToStream(mapping, os)
  }

}

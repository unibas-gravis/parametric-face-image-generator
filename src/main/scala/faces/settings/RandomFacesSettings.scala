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
package faces.settings

import java.io.{File, PrintWriter}

import scala.io.Source

case class RandomFacesSettings(  override val outputLocation: OutputLocation,
                                 override val backgrounds: Backgrounds,
                                 override val renderingMethods: RenderingMethods,
                                 override val morphableModelParameters: MorphableModelParameters,
                                 override val imageDimensions: ImageDimensions,
                                 override val defaultParameters: DefaultParameters,
                                 override val landmarkTags: IndexedSeq[String],
                                 override val occlusionMode: String,
                                 illuminationParameters: IlluminationParameters,
                                 poseVariation: RandomPoseVariation
                              ) extends FacesSettings {
}

object RandomFacesSettings  {
  import RandomFacesSettingsJsonFormatV1._
  import spray.json._

  def write(setting: RandomFacesSettings, file: File): Unit = {

    val writer = new PrintWriter(file)
    writer.println(setting.toJson prettyPrint)
    writer.flush()
    writer.close()
  }

  def read(file: File): RandomFacesSettings = {
    val source = Source.fromFile(file)
    read(source)
  }

  def read(source: Source): RandomFacesSettings = {

    val content = try source.getLines mkString "\n" finally source.close()
    content.parseJson.convertTo[RandomFacesSettings]
  }

}
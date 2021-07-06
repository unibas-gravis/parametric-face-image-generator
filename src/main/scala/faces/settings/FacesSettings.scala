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

import faces.utils.{BaselIlluminationPrior, Distribution}
import scalismo.color.RGBA
import scalismo.faces.mesh.TextureMappedProperty
import scalismo.faces.parameters._
import scalismo.geometry.{EuclideanVector, _3D}

abstract class FacesSettings{
  val general : General
  val outputLocation: OutputLocation
  val backgrounds: Backgrounds
  val renderingMethods: RenderingMethods
  val morphableModelParameters: MorphableModelParameters
  val imageDimensions: ImageDimensions
  val defaultParameters: DefaultParameters
  val landmarkTags: IndexedSeq[String]
  val regionMaps: IndexedSeq[TextureMappedPropertyDescription]
}


case class TextureMappedPropertyDescription(
                                             name: String,
                                             mapping: String,
                                             map: String
                                           )
case class General(
                    seed : Int
                  )

case class OutputLocation(
                           outPath: String
                         ) {
  def outImgPath: String = outPath + "img/"
  def outRpsPath: String = outPath + "rps/"
  def outCSVPath: String = outPath + "csv/"
  def outTLMSPath: String = outPath + "tlms/"

}

case class Backgrounds(
                        bgPath: String,
                        bg: Boolean,
                        bgType: String
                      )

case class MorphableModelParameters(
                                     nIds: Int,
                                     nSamples: Int,
                                     nShape: Int,
                                     nColor: Int,
                                     expressions: Boolean,
                                     nExpression: Int,
                                     colorDistribution: Distribution,
                                     shapeDistribution: Distribution,
                                     expressionDistribution: Distribution,
                                     modelFn: String
                                   )

case class IlluminationParameters(
                                   illumination: String,
                                   illuminationPriorFn: String,
                                   illuminationPriorNoColor: Boolean,
                                   illuminationPriorFixEnergy: Boolean,
                                   illuminationPriorFixEnergyValue: Double,
                                   directionalLight: DirectionalLight
                                 ) {
  def illuminationPrior = BaselIlluminationPrior(illuminationPriorFn, illuminationPriorNoColor, illuminationPriorFixEnergy, illuminationPriorFixEnergyValue)
}



case class RandomPoseVariation(
                                yawDistribution: Distribution,
                                rollDistribution: Distribution,
                                pitchDistribution: Distribution,
                                xTranslationDistribution: Distribution,
                                yTranslationDistribution: Distribution,
                                scalingDistribution: Distribution,
                                faceCenter: String
                              )

case class ControlledIlluminationVariation(
                                            illuminationDirectionRange: Range
                                          )

case class ControlledPoseVariation(
                                    yawRange: Range,
                                    rollRange: Range,
                                    pitchRange: Range,
                                    faceCenter: String
                                  )

case class ControlledBackgroundVariation(
                                          backgroundRange: Range
                                        )

case class ImageDimensions(
                            imageWidth: Int,
                            imageHeight: Int
                          )


case class DefaultParameters(
                              pose: Pose,
                              view: ViewParameter,
                              camera: Camera,
                              colorTransform: ColorTransform
                            )

case class RenderingMethods(render: Boolean, renderDepthMap: Boolean, renderColorCorrespondenceImage: Boolean, renderNormals: Boolean, renderAlbedo: Boolean, renderIllumination: Boolean, renderRegionMaps: Boolean)
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
import scalismo.faces.parameters._

abstract class FacesSettings{
  val outputLocation: OutputLocation
  val backgrounds: Backgrounds
  val morphableModelParameters: MorphableModelParameters
  val imageDimensions: ImageDimensions
  val defaultParameters: DefaultParameters
}


case class OutputLocation(
                           outPath: String
                         ) {
  def outImgPath: String = outPath + "img/"
  def outRpsPath: String = outPath + "rps/"
  def outCSVPath: String = outPath + "csv/"

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
                                     modelFn: String
                                   )

case class IlluminationParameters(
                                   illumination: String,
                                   illuminationPriorFn: String,
                                   directionalLight: DirectionalLight
                                 ) {
  def illuminationPrior = BaselIlluminationPrior(illuminationPriorFn)
}



case class RandomPoseVariation(
                                yawDistribution: Distribution,
                                rollDistribution: Distribution,
                                pitchDistribution: Distribution,
                                xTranslationDistribution: Distribution,
                                yTranslationDistribution: Distribution,
                                scalingDistribution: Distribution,
                                faceCenter: Boolean
                              )

case class ControlledIlluminationVariation(
                                            illuminationDirectionRange: Range
                                          )

case class ControlledPoseVariation(
                                    yawRange: Range,
                                    rollRange: Range,
                                    pitchRange: Range,
                                    faceCenter: Boolean
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
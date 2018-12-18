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
package faces.renderer

import scalismo.faces.color.RGBA
import scalismo.faces.image.PixelImage
import scalismo.faces.mesh.TextureMappedProperty
import scalismo.faces.parameters.{ParametricRenderer, RenderParameter}
import scalismo.faces.sampling.face.{CorrespondenceColorImageRenderer, CorrespondenceMoMoRenderer, RenderFromCorrespondenceImage}

case class ColorMapRenderer(colorMap: TextureMappedProperty[RGBA], correspondenceMoMoRenderer: CorrespondenceMoMoRenderer) extends RenderFromCorrespondenceImage[RGBA](correspondenceMoMoRenderer){

  override def renderImage(parameters: RenderParameter): PixelImage[RGBA] = {
    val face = correspondenceMoMoRenderer.instance(parameters)
    ParametricRenderer.renderPropertyImage(parameters,face.shape,colorMap).map(_.getOrElse(RGBA.BlackTransparent))
  }

}

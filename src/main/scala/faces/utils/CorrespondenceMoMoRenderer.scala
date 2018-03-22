package faces.utils

/*
 * Copyright University of Basel, Graphics and Vision Research Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import breeze.linalg.DenseVector
import scalismo.faces.color.RGBA
import scalismo.faces.image.PixelImage
import scalismo.faces.landmarks.TLMSLandmark2D
import scalismo.faces.mesh.VertexColorMesh3D
import scalismo.faces.momo.MoMo
import scalismo.faces.parameters.{ParametricRenderer, RenderParameter}
import scalismo.faces.render.{PixelShader, TriangleRenderer}
import scalismo.faces.render.TriangleRenderer.TriangleFragment
import scalismo.faces.sampling.face._
import scalismo.geometry.Point
import scalismo.mesh.MeshSurfaceProperty
import scalismo.utils.Memoize

/** parametric renderer for a Morphable Model, implements all useful Parameteric*Renderer interfaces */
class CorrespondenceMoMoRenderer(val model: MoMo, val clearColor: RGBA)
  extends ParametricImageRenderer[RGBA]
    with ParametricLandmarksRenderer
    with ParametricMaskRenderer
    with ParametricMeshRenderer
    with ParametricModel {

  /** pad a coefficient vector if it is too short, basis with single vector */
  private def padCoefficients(coefficients: DenseVector[Double], rank: Int): DenseVector[Double] = {
    require(coefficients.length <= rank, "too many coefficients for model")
    if (coefficients.length == rank)
      coefficients
    else
      DenseVector(coefficients.toArray ++ Array.fill(rank - coefficients.length)(0.0))
  }

  /** create an instance of the model, in the original model's object coordinates */
  override def instance(parameters: RenderParameter): VertexColorMesh3D = {
    model.instance(parameters.momo.coefficients)
  }

  def renderCorrespondenceImage(parameters: RenderParameter): PixelImage[Option[TriangleFragment]] = {
    val inst = instance(parameters)
    TriangleRenderer.renderCorrespondenceImage(inst.shape, parameters.pointShader, parameters.imageSize.width, parameters.imageSize.height)
  }

  /** render the image described by the parameters */
  override def renderImage(parameters: RenderParameter): PixelImage[RGBA] = {
    val correspondenceImage = renderCorrespondenceImage(parameters)
    val inst = instance(parameters)
    val shader: PixelShader[RGBA] = parameters.pixelShader(inst)
    correspondenceImage.map{ px => if(px.isDefined) shader(px.get) else clearColor }
  }

  /** render the mesh described by the parameters, draws instance from model and places properly in the world (world coordinates) */
  override def renderMesh(parameters: RenderParameter): VertexColorMesh3D = {
    val t = parameters.pose.transform
    val mesh = instance(parameters)
    VertexColorMesh3D(
      mesh.shape.transform(p => t(p)),
      mesh.color
    )
  }

  /** render landmark position in the image */
  override def renderLandmark(lmId: String, parameter: RenderParameter): Option[TLMSLandmark2D] = {
    val renderer = parameter.renderTransform
    for {
      ptId <- model.landmarkPointId(lmId)
      lm3d <- Some(model.instanceAtPoint(parameter.momo.coefficients, ptId)._1)
      lmImage <- Some(renderer(lm3d))
    } yield TLMSLandmark2D(lmId, Point(lmImage.x, lmImage.y), visible = true)
  }

  /** checks the availability of a named landmark */
  override def hasLandmarkId(lmId: String): Boolean = model.landmarkPointId(lmId).isDefined


  /** get all available landmarks */
  override def allLandmarkIds: IndexedSeq[String] = model.landmarks.keySet.toIndexedSeq


  /** render a mask defined on the model to image space */
  override def renderMask(parameters: RenderParameter, mask: MeshSurfaceProperty[Int]): PixelImage[Int] = {
    val inst = instance(parameters)
    val maskImage = ParametricRenderer.renderPropertyImage(parameters, inst.shape, mask)
    maskImage.map(_.getOrElse(0)) // 0 - invalid, outside rendering area
  }

  /** get a cached version of this renderer */
  def cached(cacheSize: Int) = new CorrespondenceMoMoRenderer(model, clearColor) {
    private val imageRenderer = Memoize(super.renderImage, cacheSize)
    private val correspondenceImageRenderer = Memoize(super.renderCorrespondenceImage, cacheSize)
    private val meshRenderer = Memoize(super.renderMesh, cacheSize)
    private val maskRenderer = Memoize((super.renderMask _).tupled, cacheSize)
    private val lmRenderer = Memoize((super.renderLandmark _).tupled, cacheSize * allLandmarkIds.length)
    private val instancer = Memoize(super.instance, cacheSize)

    override def renderImage(parameters: RenderParameter): PixelImage[RGBA] = imageRenderer(parameters)
    override def renderCorrespondenceImage(parameters: RenderParameter): PixelImage[Option[TriangleFragment]] = correspondenceImageRenderer(parameters)
    override def renderLandmark(lmId: String, parameter: RenderParameter): Option[TLMSLandmark2D] = lmRenderer((lmId, parameter))
    override def renderMesh(parameters: RenderParameter): VertexColorMesh3D = meshRenderer(parameters)
    override def instance(parameters: RenderParameter): VertexColorMesh3D = instancer(parameters)
    override def renderMask(parameters: RenderParameter, mask: MeshSurfaceProperty[Int]): PixelImage[Int] = maskRenderer((parameters, mask))
  }
}

object CorrespondenceMoMoRenderer {
  def apply(model: MoMo, clearColor: RGBA) = new CorrespondenceMoMoRenderer(model, clearColor)
  def apply(model: MoMo) = new CorrespondenceMoMoRenderer(model, RGBA.BlackTransparent)
}

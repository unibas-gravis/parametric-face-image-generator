package faces.utils

import scalismo.faces.color.RGBA
import scalismo.faces.image.PixelImage
import scalismo.faces.parameters.RenderParameter
import scalismo.faces.sampling.face.ModalityRenderers.{DepthMapRenderer, NormalsRenderer}
import scalismo.faces.sampling.face.{CorrespondenceMoMoRenderer, RenderFromCorrespondenceImage}


case class DepthMapRendererRGBA(correspondenceMoMoRenderer: CorrespondenceMoMoRenderer) extends RenderFromCorrespondenceImage[RGBA](correspondenceMoMoRenderer: CorrespondenceMoMoRenderer) {
  val deptMapRenderer = DepthMapRenderer(correspondenceMoMoRenderer)
  override def renderImage(parameters: RenderParameter): PixelImage[RGBA] = {
    val depthMap = deptMapRenderer.renderImage(parameters)

    //visualization
    val values  = depthMap.values.toIndexedSeq.flatten
    val ma = values.max
    val mi = values.min
    val mami = ma-mi
    depthMap.map{d=>
      if(d.isEmpty)
        RGBA(0.0)
      else {
        RGBA(1.0 - (d.get - mi)/mami)
      }
    }
  }
}

case class NormalsRendererRGBA(correspondenceMoMoRenderer: CorrespondenceMoMoRenderer, clearColor: RGBA = RGBA.BlackTransparent) extends RenderFromCorrespondenceImage[RGBA](correspondenceMoMoRenderer: CorrespondenceMoMoRenderer) {
 val normalsRenderer = NormalsRenderer(correspondenceMoMoRenderer)
  override def renderImage(parameters: RenderParameter): PixelImage[RGBA] = {
    normalsRenderer.renderNormalsVisualization(parameters, clearColor)
  }
}

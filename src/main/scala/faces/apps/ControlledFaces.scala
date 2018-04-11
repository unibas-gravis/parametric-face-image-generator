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
 *
 */
package faces.apps

import java.io.File

import faces.settings.ControlledFacesSettings
import faces.utils.{Helpers, InfiniteDataGeneratorOptions}
import scalismo.faces.color.{RGB, RGBA}
import scalismo.faces.io.PixelImageIO
import scalismo.faces.parameters._
import scalismo.geometry.Vector
import scalismo.utils.Random

object ControlledFaces extends App {

  scalismo.initialize()
  implicit val rnd: Random = Random(1986) // use random seed to reproduce your results

  //****************************************************************************
  // SETTINGS
  //****************************************************************************

  val opt = new InfiniteDataGeneratorOptions(args)
  opt.verify()

  val cfg = ControlledFacesSettings.read(new File(opt.configurationFile()))
  import cfg.backgrounds._
  import cfg.morphableModelParameters._
  import cfg.imageDimensions._
  import cfg.defaultParameters._
  import cfg.illuminationVariation._
  import cfg.poseVariation._
  import cfg.backgroundVariation._

  val helpers = Helpers(cfg)

  //****************************************************************************
  // CONTROLLED SAMPLE GENERATOR
  //****************************************************************************

  (0 until nIds).par.foreach( id =>{
    try{
      // generate random model instance (shape and color)
      val rndId = helpers.rndMoMoInstance
      var n = 0
      for(b <- backgroundRange){
        for(y <- yawRange){
          for(p <- pitchRange){
            for(r <- rollRange){
              for(i <- illuminationDirectionRange){
                n+=1

                val controlledPose = Pose(
                  pose.scaling,
                  pose.translation,
                  scala.math.toRadians(r),
                  scala.math.toRadians(y),
                  scala.math.toRadians(p))

                val controlledIll = SphericalHarmonicsLight.fromAmbientDiffuse(RGB(0.5), RGB(0.5), Vector.fromSpherical(1.0, math.Pi/3.0, (i*math.Pi/180.0)+(math.Pi/2.0)))

                // put RenderParameters together of all its randomized parts
                val uncentered = RenderParameter(controlledPose, view, camera, controlledIll, DirectionalLight.off, rndId, ImageSize(imageWidth, imageHeight), colorTransform)

                // move face in the middle of the image
                val centered = if (faceCenter == "facebox")
                {
                  helpers.centerFaceBox(uncentered)
                }
                else if(faceCenter == "landmark")
                {
                  helpers.centerLandmark(uncentered)
                }
                else{
                  uncentered
                }

                val imageData =
                  for((postfix, currentRenderer) <- helpers.renderingMethods) yield {
                    if (bg && postfix == "") {
                      require(helpers.loadBgs.nonEmpty, "no Background files with type " + cfg.backgrounds.bgType + " found in " + cfg.backgrounds.bgPath)
                      val BG = helpers.loadBgs(b)
                      val controlledBGimg = PixelImageIO.read[RGBA](BG).get.resample(imageWidth, imageHeight)
                      (currentRenderer.renderImage(centered).zip(controlledBGimg).map(p => if (p._1.a < 0.5) p._2 else p._1), postfix)
                    }
                    else {
                      (currentRenderer.renderImage(centered), postfix)
                    }
                  }

                // write images and their parameters
                println(s"Generating \t ID:$id \t Sample:$n")
                for((img, postifx) <- imageData) {
                  helpers.writeRenderParametersAndLandmarks(centered, id, n, opt.landmarks.toOption)
                  helpers.writeImg(img, id, n, postifx)
                }

              }
            }
          }
        }
      }
    } catch{
      case e: Throwable =>
        println("Something went wrong with id: " + id)
        println(s"${e.getMessage}")
        println(s"${e.getStackTrace}")
    }
  })

}
import java.io.File

import faces.settings._
import faces.utils.{ConstantDistribution, GaussianDistribution, MixtureDistribution, UniformDistribution}
import org.scalatest.{FunSpec, Matchers}
import scalismo.faces.parameters._
import scalismo.geometry.{Point2D, Vector, Vector3D}
import scalismo.utils.Random

import scala.io.Source

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

class RandomFacesSettingsTest extends FunSpec with Matchers {
  scalismo.initialize()

  implicit val rnd: Random = Random(43)

  describe("RandomFacesSettings") {


    val Default = RandomFacesSettings(
      OutputLocation(outPath = "data/output/"),
      Backgrounds(
        bgPath = "data/backgrounds/",
        bg = true,
        bgType = ".jpg"),
      RenderingMethods(
        render = true,
        renderDepthMap = true,
        renderColorCorrespondenceImage = true,
        renderNormals= true,
        renderAlbedo = true,
        renderIllumination = true
      ),
      MorphableModelParameters(
        nIds = 2,
        nSamples = 5,
        nShape = 199,
        nColor = 199,
        expressions = true,
        nExpression = 100,
        modelFn = "data/bfm2017/model2017-1_face12_nomouth.h5"),
      ImageDimensions(imageWidth = 227,
        imageHeight = 227),
      DefaultParameters(pose = Pose.away1m,
        view = ViewParameter.neutral,
        camera = Camera(
          focalLength = 50,
          principalPoint = Point2D.origin,
          sensorSize = Vector(15.0, 15.0),
          near = 10,
          far = 1000e3,
          orthographic = false),
        colorTransform = ColorTransform.neutral),
      IndexedSeq(
        "center.chin.tip",
        "center.lips.lower.inner",
        "center.nose.tip",
        "left.ear.lobule.attachement",
        "right.ear.lobule.attachement",
        "left.eye.corner_outer",
        "left.eye.corner_inner",
        "left.eye.pupil.center",
        "right.eye.corner_outer",
        "right.eye.corner_inner",
        "right.eye.pupil.center",
        "left.eyebrow.bend.lower",
        "left.eyebrow.inner_lower",
        "right.eyebrow.bend.lower",
        "right.eyebrow.inner_lower",
        "left.lips.corner",
        "right.lips.corner",
        "left.nose.wing.tip",
        "right.nose.wing.tip"
      ),
      "none",
      IlluminationParameters(illumination = "multiVariateNormal",
        illuminationPriorFn = "data/bip/parameters/",
        illuminationPriorNoColor = true,
        illuminationPriorFixEnergy = true,
        illuminationPriorFixEnergyValue = 6.33,
        directionalLight = DirectionalLight.off),
      RandomPoseVariation(yawDistribution = MixtureDistribution(Seq(
        (0.5, UniformDistribution(-90,90)),
        (0.3,GaussianDistribution(-3,2)),
        (0.2,ConstantDistribution(0.0))
      )),
        pitchDistribution = GaussianDistribution(-30,30),
        rollDistribution = ConstantDistribution(30),
        xTranslationDistribution = UniformDistribution(-5,5),
        yTranslationDistribution = UniformDistribution(-5,5),
        scalingDistribution = UniformDistribution(1.0,1.2),
        faceCenter = "facebox")
    )


    it("can save and load without altering the parameters") {
      val f = File.createTempFile("random-faces-settings-io-test-file", ".txt")
      f.deleteOnExit()

      val default = Default
      RandomFacesSettings.write(default,f)
      val cfg = RandomFacesSettings.read(f)

      default.outputLocation.outPath shouldBe cfg.outputLocation.outPath

      default.backgrounds.bg shouldBe cfg.backgrounds.bg
      default.backgrounds.bgPath shouldBe cfg.backgrounds.bgPath
      default.backgrounds.bgType shouldBe cfg.backgrounds.bgType

      default.morphableModelParameters.nIds shouldBe cfg.morphableModelParameters.nIds
      default.morphableModelParameters.nSamples shouldBe cfg.morphableModelParameters.nSamples
      default.morphableModelParameters.modelFn shouldBe cfg.morphableModelParameters.modelFn
      default.morphableModelParameters.nShape shouldBe cfg.morphableModelParameters.nShape
      default.morphableModelParameters.nColor shouldBe cfg.morphableModelParameters.nColor
      default.morphableModelParameters.nExpression shouldBe cfg.morphableModelParameters.nExpression
      default.morphableModelParameters.expressions shouldBe cfg.morphableModelParameters.expressions

      default.illuminationParameters.illumination shouldBe cfg.illuminationParameters.illumination
      default.illuminationParameters.illuminationPriorFn shouldBe cfg.illuminationParameters.illuminationPriorFn
      default.illuminationParameters.illuminationPriorNoColor shouldBe cfg.illuminationParameters.illuminationPriorNoColor
      default.illuminationParameters.illuminationPriorFixEnergy shouldBe cfg.illuminationParameters.illuminationPriorFixEnergy
      default.illuminationParameters.illuminationPriorFixEnergyValue shouldBe cfg.illuminationParameters.illuminationPriorFixEnergyValue

      default.illuminationParameters.directionalLight shouldBe cfg.illuminationParameters.directionalLight

      default.poseVariation.yawDistribution shouldBe cfg.poseVariation.yawDistribution
      default.poseVariation.rollDistribution shouldBe cfg.poseVariation.rollDistribution
      default.poseVariation.pitchDistribution shouldBe cfg.poseVariation.pitchDistribution
      default.poseVariation.xTranslationDistribution shouldBe cfg.poseVariation.xTranslationDistribution
      default.poseVariation.yTranslationDistribution shouldBe cfg.poseVariation.yTranslationDistribution
      default.poseVariation.scalingDistribution shouldBe cfg.poseVariation.scalingDistribution
      default.poseVariation.faceCenter shouldBe cfg.poseVariation.faceCenter

      default.imageDimensions.imageWidth shouldBe cfg.imageDimensions.imageWidth
      default.imageDimensions.imageHeight shouldBe cfg.imageDimensions.imageHeight

      default.defaultParameters.pose shouldBe cfg.defaultParameters.pose
      default.defaultParameters.view shouldBe cfg.defaultParameters.view
      default.defaultParameters.camera shouldBe cfg.defaultParameters.camera
      default.defaultParameters.colorTransform shouldBe cfg.defaultParameters.colorTransform


      default.outputLocation shouldBe cfg.outputLocation
      default.backgrounds shouldBe cfg.backgrounds
      default.morphableModelParameters shouldBe cfg.morphableModelParameters
      default.illuminationParameters shouldBe cfg.illuminationParameters
      default.poseVariation shouldBe cfg.poseVariation
      default.imageDimensions shouldBe cfg.imageDimensions
      default.defaultParameters shouldBe cfg.defaultParameters
      default.landmarkTags shouldBe cfg.landmarkTags
      default.occlusionMode shouldBe cfg.occlusionMode

      cfg shouldBe default
    }


    it("can load parameters with additional comments") {
      val stream = getClass.getResourceAsStream("randomFacesSettings.json")
      val source = Source.fromInputStream(stream)
      val cfg = RandomFacesSettings.read(source)

      val default = Default

      default.outputLocation.outPath shouldBe cfg.outputLocation.outPath

      default.backgrounds.bg shouldBe cfg.backgrounds.bg
      default.backgrounds.bgPath shouldBe cfg.backgrounds.bgPath
      default.backgrounds.bgType shouldBe cfg.backgrounds.bgType

      default.morphableModelParameters.nIds shouldBe cfg.morphableModelParameters.nIds
      default.morphableModelParameters.nSamples shouldBe cfg.morphableModelParameters.nSamples
      default.morphableModelParameters.modelFn shouldBe cfg.morphableModelParameters.modelFn
      default.morphableModelParameters.nShape shouldBe cfg.morphableModelParameters.nShape
      default.morphableModelParameters.nColor shouldBe cfg.morphableModelParameters.nColor
      default.morphableModelParameters.nExpression shouldBe cfg.morphableModelParameters.nExpression
      default.morphableModelParameters.expressions shouldBe cfg.morphableModelParameters.expressions

      default.illuminationParameters.illumination shouldBe cfg.illuminationParameters.illumination
      default.illuminationParameters.illuminationPriorFn shouldBe cfg.illuminationParameters.illuminationPriorFn
      default.illuminationParameters.directionalLight shouldBe cfg.illuminationParameters.directionalLight

      default.poseVariation.yawDistribution shouldBe cfg.poseVariation.yawDistribution
      default.poseVariation.rollDistribution shouldBe cfg.poseVariation.rollDistribution
      default.poseVariation.pitchDistribution shouldBe cfg.poseVariation.pitchDistribution
      default.poseVariation.xTranslationDistribution shouldBe cfg.poseVariation.xTranslationDistribution
      default.poseVariation.yTranslationDistribution shouldBe cfg.poseVariation.yTranslationDistribution
      default.poseVariation.scalingDistribution shouldBe cfg.poseVariation.scalingDistribution
      default.poseVariation.faceCenter shouldBe cfg.poseVariation.faceCenter

      default.imageDimensions.imageWidth shouldBe cfg.imageDimensions.imageWidth
      default.imageDimensions.imageHeight shouldBe cfg.imageDimensions.imageHeight

      default.defaultParameters.pose shouldBe cfg.defaultParameters.pose
      default.defaultParameters.view shouldBe cfg.defaultParameters.view
      default.defaultParameters.camera shouldBe cfg.defaultParameters.camera
      default.defaultParameters.colorTransform shouldBe cfg.defaultParameters.colorTransform


      default.outputLocation shouldBe cfg.outputLocation
      default.backgrounds shouldBe cfg.backgrounds
      default.morphableModelParameters shouldBe cfg.morphableModelParameters
      default.illuminationParameters shouldBe cfg.illuminationParameters
      default.poseVariation shouldBe cfg.poseVariation
      default.imageDimensions shouldBe cfg.imageDimensions
      default.defaultParameters shouldBe cfg.defaultParameters
      default.landmarkTags shouldBe cfg.landmarkTags
      default.occlusionMode shouldBe cfg.occlusionMode

      cfg shouldBe default
    }
  }





  describe("ControlledFacesSettings") {


    val Default = ControlledFacesSettings(
      OutputLocation(outPath = "data/output/"),
      Backgrounds(
        bgPath = "data/backgrounds/",
        bg = true,
        bgType = ".jpg"),
      RenderingMethods(
        render = true,
        renderDepthMap = true,
        renderColorCorrespondenceImage = true,
        renderNormals= true,
        renderAlbedo = true,
        renderIllumination = true
      ),
      MorphableModelParameters(
        nIds = 2,
        nSamples = 5,
        nShape = 199,
        nColor = 199,
        expressions = true,
        nExpression = 100,
        modelFn = "data/bfm2017/model2017-1_face12_nomouth.h5"),
      ImageDimensions(imageWidth = 227,
        imageHeight = 227),
      DefaultParameters(pose = Pose.away1m,
        view = ViewParameter.neutral,
        camera = Camera(
          focalLength = 50,
          principalPoint = Point2D.origin,
          sensorSize = Vector(15.0, 15.0),
          near = 10,
          far = 1000e3,
          orthographic = false),
        colorTransform = ColorTransform.neutral),
      IndexedSeq(
        "center.chin.tip",
        "center.lips.lower.inner",
        "center.nose.tip",
        "left.ear.lobule.attachement",
        "right.ear.lobule.attachement",
        "left.eye.corner_outer",
        "left.eye.corner_inner",
        "left.eye.pupil.center",
        "right.eye.corner_outer",
        "right.eye.corner_inner",
        "right.eye.pupil.center",
        "left.eyebrow.bend.lower",
        "left.eyebrow.inner_lower",
        "right.eyebrow.bend.lower",
        "right.eyebrow.inner_lower",
        "left.lips.corner",
        "right.lips.corner",
        "left.nose.wing.tip",
        "right.nose.wing.tip"
      ), "none",
      ControlledIlluminationVariation(0 until 10 by 1),
      ControlledPoseVariation(yawRange = -90 to 90 by 45,
        rollRange = -15 to 15 by 5,
        pitchRange = -15 to 15 by 3,
        faceCenter = "facebox"),
      ControlledBackgroundVariation(0 until 2 by 1)
    )


    it("can save and load without altering the parameters") {
      val f = File.createTempFile("controlled-faces-settings-io-test-file", ".txt")
      f.deleteOnExit()

      val default = Default
      ControlledFacesSettings.write(default,f)
      val cfg = ControlledFacesSettings.read(f)

      default.outputLocation.outPath shouldBe cfg.outputLocation.outPath

      default.backgrounds.bg shouldBe cfg.backgrounds.bg
      default.backgrounds.bgPath shouldBe cfg.backgrounds.bgPath
      default.backgrounds.bgType shouldBe cfg.backgrounds.bgType

      default.morphableModelParameters.nIds shouldBe cfg.morphableModelParameters.nIds
      default.morphableModelParameters.nSamples shouldBe cfg.morphableModelParameters.nSamples
      default.morphableModelParameters.modelFn shouldBe cfg.morphableModelParameters.modelFn
      default.morphableModelParameters.nShape shouldBe cfg.morphableModelParameters.nShape
      default.morphableModelParameters.nColor shouldBe cfg.morphableModelParameters.nColor
      default.morphableModelParameters.nExpression shouldBe cfg.morphableModelParameters.nExpression
      default.morphableModelParameters.expressions shouldBe cfg.morphableModelParameters.expressions

      default.illuminationVariation.illuminationDirectionRange shouldBe cfg.illuminationVariation.illuminationDirectionRange
      default.backgroundVariation.backgroundRange shouldBe cfg.backgroundVariation.backgroundRange

      default.poseVariation.yawRange shouldBe cfg.poseVariation.yawRange
      default.poseVariation.rollRange shouldBe cfg.poseVariation.rollRange
      default.poseVariation.pitchRange shouldBe cfg.poseVariation.pitchRange
      default.poseVariation.faceCenter shouldBe cfg.poseVariation.faceCenter

      default.imageDimensions.imageWidth shouldBe cfg.imageDimensions.imageWidth
      default.imageDimensions.imageHeight shouldBe cfg.imageDimensions.imageHeight

      default.defaultParameters.pose shouldBe cfg.defaultParameters.pose
      default.defaultParameters.view shouldBe cfg.defaultParameters.view
      default.defaultParameters.camera shouldBe cfg.defaultParameters.camera
      default.defaultParameters.colorTransform shouldBe cfg.defaultParameters.colorTransform


      default.outputLocation shouldBe cfg.outputLocation
      default.backgrounds shouldBe cfg.backgrounds
      default.morphableModelParameters shouldBe cfg.morphableModelParameters
      default.backgroundVariation shouldBe cfg.backgroundVariation
      default.illuminationVariation shouldBe cfg.illuminationVariation
      default.poseVariation shouldBe cfg.poseVariation
      default.imageDimensions shouldBe cfg.imageDimensions
      default.defaultParameters shouldBe cfg.defaultParameters
      default.landmarkTags shouldBe cfg.landmarkTags
      default.occlusionMode shouldBe cfg.occlusionMode

      cfg shouldBe default
    }


    it("can load parameters with additional comments") {
      val stream = getClass.getResourceAsStream("controlledFacesSettings.json")
      val source = Source.fromInputStream(stream)
      val cfg = ControlledFacesSettings.read(source)

      val default = Default

      default.outputLocation.outPath shouldBe cfg.outputLocation.outPath

      default.backgrounds.bg shouldBe cfg.backgrounds.bg
      default.backgrounds.bgPath shouldBe cfg.backgrounds.bgPath
      default.backgrounds.bgType shouldBe cfg.backgrounds.bgType

      default.morphableModelParameters.nIds shouldBe cfg.morphableModelParameters.nIds
      default.morphableModelParameters.nSamples shouldBe cfg.morphableModelParameters.nSamples
      default.morphableModelParameters.modelFn shouldBe cfg.morphableModelParameters.modelFn
      default.morphableModelParameters.nShape shouldBe cfg.morphableModelParameters.nShape
      default.morphableModelParameters.nColor shouldBe cfg.morphableModelParameters.nColor
      default.morphableModelParameters.nExpression shouldBe cfg.morphableModelParameters.nExpression
      default.morphableModelParameters.expressions shouldBe cfg.morphableModelParameters.expressions

      default.illuminationVariation.illuminationDirectionRange shouldBe cfg.illuminationVariation.illuminationDirectionRange
      default.backgroundVariation.backgroundRange shouldBe cfg.backgroundVariation.backgroundRange

      default.poseVariation.yawRange shouldBe cfg.poseVariation.yawRange
      default.poseVariation.rollRange shouldBe cfg.poseVariation.rollRange
      default.poseVariation.pitchRange shouldBe cfg.poseVariation.pitchRange
      default.poseVariation.faceCenter shouldBe cfg.poseVariation.faceCenter

      default.imageDimensions.imageWidth shouldBe cfg.imageDimensions.imageWidth
      default.imageDimensions.imageHeight shouldBe cfg.imageDimensions.imageHeight

      default.defaultParameters.pose shouldBe cfg.defaultParameters.pose
      default.defaultParameters.view shouldBe cfg.defaultParameters.view
      default.defaultParameters.camera shouldBe cfg.defaultParameters.camera
      default.defaultParameters.colorTransform shouldBe cfg.defaultParameters.colorTransform


      default.outputLocation shouldBe cfg.outputLocation
      default.backgrounds shouldBe cfg.backgrounds
      default.morphableModelParameters shouldBe cfg.morphableModelParameters
      default.backgroundVariation shouldBe cfg.backgroundVariation
      default.illuminationVariation shouldBe cfg.illuminationVariation
      default.poseVariation shouldBe cfg.poseVariation
      default.imageDimensions shouldBe cfg.imageDimensions
      default.defaultParameters shouldBe cfg.defaultParameters
      default.landmarkTags shouldBe cfg.landmarkTags
      default.occlusionMode shouldBe cfg.occlusionMode

      cfg shouldBe default
    }
  }




}

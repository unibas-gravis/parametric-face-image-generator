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

import faces.utils._
import scalismo.faces.parameters._
import scalismo.geometry.{Vector, _3D}
import spray.json._

import scala.collection.immutable.ListMap


object RandomFacesSettingsJsonFormatV1 {

  import FacesSettingsJsonFormatV1._
  import scalismo.faces.io.renderparameters.RenderParameterJSONFormatV4._

  implicit val ReneringMethodsFormat = RenderingMethodsJsonFormatV1.RenderingMethodsFormat

  implicit val IlluminationParametersFormat: RootJsonFormat[IlluminationParameters] = new RootJsonFormat[IlluminationParameters] {
    override def write(obj: IlluminationParameters): JsValue = {
      new JsObjectOrdered( ListMap(
        ("illumination-type", obj.illumination.toJson),
        ("illumination-prior-directory", obj.illuminationPriorFn.toJson),
        ("illumination-prior-no-color", obj.illuminationPriorNoColor.toJson),
        ("illumination-prior-fix-energy", obj.illuminationPriorFixEnergy.toJson),
        ("illumination-prior-fix-energy-value", obj.illuminationPriorFixEnergyValue.toJson),
        ("directional-light", obj.directionalLight.toJson)
      ))
    }
    override def read(json: JsValue): IlluminationParameters = {
      val fields = json.asJsObject(s"expected IlluminationParameters object, got: $json").fields

      val illumination = fields("illumination-type").convertTo[String]
      val illuminationPriorFn = fields("illumination-prior-directory").convertTo[String]
      val illuminationPriorNoColor = fields("illumination-prior-no-color").convertTo[Boolean]
      val illuminationPriorFixEnergy = fields("illumination-prior-fix-energy").convertTo[Boolean]
      val illuminationPriorFixEnergyValue = fields("illumination-prior-fix-energy-value").convertTo[Double]
      val directionalLight = fields("directional-light").convertTo[DirectionalLight]

      IlluminationParameters(
        illumination = illumination,
        illuminationPriorFn = illuminationPriorFn,
        illuminationPriorNoColor = illuminationPriorNoColor,
        illuminationPriorFixEnergy = illuminationPriorFixEnergy,
        illuminationPriorFixEnergyValue = illuminationPriorFixEnergyValue,
        directionalLight = directionalLight
      )
    }
  }


  implicit val PoseVariationFormat: RootJsonFormat[RandomPoseVariation] = new RootJsonFormat[RandomPoseVariation] {
    override def write(obj: RandomPoseVariation): JsValue = {
      new JsObjectOrdered( ListMap(
        ("yaw-distribution", obj.yawDistribution.toJson),
        ("roll-distribution", obj.rollDistribution.toJson),
        ("pitch-distribution", obj.pitchDistribution.toJson),
        ("x-translation-distribution", obj.xTranslationDistribution.toJson),
        ("y-translation-distribution", obj.yTranslationDistribution.toJson),
        ("scaling-distribution", obj.scalingDistribution.toJson),
        ("center-faces", obj.faceCenter.toJson)
      ))
    }
    override def read(json: JsValue): RandomPoseVariation = {
      val fields = json.asJsObject(s"expected PoseVariation object, got: $json").fields

      val yawDistribution = fields("yaw-distribution").convertTo[Distribution]
      val rollDistribution = fields("roll-distribution").convertTo[Distribution]
      val pitchDistribution = fields("pitch-distribution").convertTo[Distribution]
      val xTranslationDistribution = fields("x-translation-distribution").convertTo[Distribution]
      val yTranslationDistribution = fields("y-translation-distribution").convertTo[Distribution]
      val scalingDistribution = fields("scaling-distribution").convertTo[Distribution]
      val faceCenter = fields("center-faces").convertTo[String]

      RandomPoseVariation(
        yawDistribution = yawDistribution,
        rollDistribution = rollDistribution,
        pitchDistribution = pitchDistribution,
        xTranslationDistribution = xTranslationDistribution,
        yTranslationDistribution = yTranslationDistribution,
        scalingDistribution = scalingDistribution,
        faceCenter = faceCenter
      )
    }
  }

  implicit val RandomFaceSettingsFormat: RootJsonFormat[RandomFacesSettings] = new RootJsonFormat[RandomFacesSettings] {
    def write(obj: RandomFacesSettings): JsValue = {
      new JsObjectOrdered(
        ListMap(
          ("output-location", obj.outputLocation.toJson),
          ("backgrounds", obj.backgrounds.toJson),
          ("rendering-methods", obj.renderingMethods.toJson),
          ("morphable-model-parameters", obj.morphableModelParameters.toJson),
          ("illumination-parameters", obj.illuminationParameters.toJson),
          ("pose-variation", obj.poseVariation.toJson),
          ("image-dimensions", obj.imageDimensions.toJson),
          ("default-parameters", obj.defaultParameters.toJson),
          ("landmark-tags", obj.landmarkTags.toJson),
          ("occlusionMode", obj.occlusionMode.toJson),
          (versionFieldName, FacesSettingsJsonFormatV1.version.toJson)
        )
      )
    }

    def read(json: JsValue): RandomFacesSettings = {
      val fields = json.asJsObject(s"expected RenderParameter object, got: $json").fields

      val fileVersion = fields(versionFieldName).convertTo[String]
      if (fileVersion != FacesSettingsJsonFormatV1.version)
        throw DeserializationException(s"V1 json reader expects ${FacesSettingsJsonFormatV1.version} json file, got: $fileVersion")

      val outputLocation = fields("output-location").convertTo[OutputLocation]
      val backgrounds = fields("backgrounds").convertTo[Backgrounds]
      val renderingMethods = fields("rendering-methods").convertTo[RenderingMethods]
      val morphableModelParameters = fields("morphable-model-parameters").convertTo[MorphableModelParameters]
      val illuminationParameters = fields("illumination-parameters").convertTo[IlluminationParameters]
      val poseVariation = fields("pose-variation").convertTo[RandomPoseVariation]
      val imageDimensions = fields("image-dimensions").convertTo[ImageDimensions]
      val defaultParameters = fields("default-parameters").convertTo[DefaultParameters]
      val landmarkTags = fields("landmark-tags").convertTo[IndexedSeq[String]]
      val occlusionMode = fields("occlusionMode").convertTo[String]

      new RandomFacesSettings(
        outputLocation,
        backgrounds,
        renderingMethods,
        morphableModelParameters,
        imageDimensions,
        defaultParameters,
        landmarkTags,
        occlusionMode,
        illuminationParameters,
        poseVariation
      )
    }
  }

}

object ControlledFacesSettingsJsonFormatV1 {
  import FacesSettingsJsonFormatV1._
  import scalismo.faces.io.renderparameters.RenderParameterJSONFormatV4._

  implicit val RangeFormat: RootJsonFormat[Range] = new RootJsonFormat[Range] {
    def write(obj: Range): JsValue = {
      val typeString = obj match {
        case _: Range.Inclusive => "inclusive"
        case _ => "not-inclusive"
      }
      new JsObjectOrdered( ListMap(
        ("type", typeString.toJson),
        ("start", obj.start.toJson),
        ("end", obj.end.toJson),
        ("step", obj.step.toJson)
      ))
    }
    def read(json: JsValue): Range = {
      val fields = json.asJsObject(s"expected RangeParameters object, got: $json").fields
      val start = fields("start").convertTo[Int]
      val end = fields("end").convertTo[Int]
      val step = fields("step").convertTo[Int]
      val typeString = fields("type").convertTo[String]
      typeString match {
        case "inclusive" => start to end by step
        case "not-inclusive" => start until end by step
        case _ => throw new IllegalArgumentException(s"The type field of a Range in a Json field can only have non-/inclusive as values. Found: $typeString")
      }
    }
  }

  implicit val BackgroundVariationFormat: RootJsonFormat[ControlledBackgroundVariation] = new RootJsonFormat[ControlledBackgroundVariation] {
    def write(obj: ControlledBackgroundVariation): JsValue = {
      new JsObjectOrdered( ListMap(
        ("back-range", obj.backgroundRange.toJson)
      ))
    }
    def read(json: JsValue): ControlledBackgroundVariation = {
      val fields = json.asJsObject(s"expected BackgroundParameters object, got: $json").fields

      val backgroundRange = fields("back-range").convertTo[Range]

      ControlledBackgroundVariation(
        backgroundRange
      )
    }
  }

  implicit val RenderingMethodsFormat = RenderingMethodsJsonFormatV1.RenderingMethodsFormat

  implicit val IlluminationParametersFormat: RootJsonFormat[ControlledIlluminationVariation] = new RootJsonFormat[ControlledIlluminationVariation] {
    def write(obj: ControlledIlluminationVariation): JsValue = {
      new JsObjectOrdered( ListMap(
        ("illu-range", obj.illuminationDirectionRange.toJson)
      ))
    }
    def read(json: JsValue): ControlledIlluminationVariation = {
      val fields = json.asJsObject(s"expected IlluminationParameters object, got: $json").fields

      val illuminationDirectionRange = fields("illu-range").convertTo[Range]

      ControlledIlluminationVariation(
        illuminationDirectionRange
      )
    }
  }


  implicit val PoseVariationFormat: RootJsonFormat[ControlledPoseVariation] = new RootJsonFormat[ControlledPoseVariation] {
    override def write(obj: ControlledPoseVariation): JsValue = {
      new JsObjectOrdered( ListMap(
        ("yaw-range", obj.yawRange.toJson),
        ("roll-range", obj.rollRange.toJson),
        ("pitch-range", obj.pitchRange.toJson),
        ("center-faces", obj.faceCenter.toJson)
      ))
    }
    override def read(json: JsValue): ControlledPoseVariation = {
      val fields = json.asJsObject(s"expected PoseVariation object, got: $json").fields

      val yawRange = fields("yaw-range").convertTo[Range]
      val rollRange = fields("roll-range").convertTo[Range]
      val pitchRange = fields("pitch-range").convertTo[Range]
      val faceCenter = fields("center-faces").convertTo[String]

      ControlledPoseVariation(
        yawRange = yawRange,
        rollRange = rollRange,
        pitchRange = pitchRange,
        faceCenter = faceCenter
      )
    }
  }

  implicit val ControlledFaceSettingsFormat: RootJsonFormat[ControlledFacesSettings] = new RootJsonFormat[ControlledFacesSettings] {
    def write(obj: ControlledFacesSettings): JsValue = {
      new JsObjectOrdered(
        ListMap(
          ("output-location", obj.outputLocation.toJson),
          ("backgrounds", obj.backgrounds.toJson),
          ("rendering-methods", obj.renderingMethods.toJson),
          ("morphable-model-parameters", obj.morphableModelParameters.toJson),
          ("background-variation", obj.backgroundVariation.toJson),
          ("illumination-angle", obj.illuminationVariation.toJson),
          ("pose-range", obj.poseVariation.toJson),
          ("image-dimensions", obj.imageDimensions.toJson),
          ("default-parameters", obj.defaultParameters.toJson),
          ("landmark-tags", obj.landmarkTags.toJson),
          ("occlusionMode", obj.occlusionMode.toJson),
          (versionFieldName, FacesSettingsJsonFormatV1.version.toJson)
        )
      )
    }

    def read(json: JsValue): ControlledFacesSettings = {
      val fields = json.asJsObject(s"expected RenderParameter object, got: $json").fields

      val fileVersion = fields(versionFieldName).convertTo[String]
      if (fileVersion != FacesSettingsJsonFormatV1.version)
        throw DeserializationException(s"V1 json reader expects ${FacesSettingsJsonFormatV1.version} json file, got: $fileVersion")

      val outputLocation = fields("output-location").convertTo[OutputLocation]
      val backgrounds = fields("backgrounds").convertTo[Backgrounds]
      val renderingMethods = fields("rendering-methods").convertTo[RenderingMethods]
      val morphableModelParameters = fields("morphable-model-parameters").convertTo[MorphableModelParameters]
      val backgroundRange = fields("background-variation").convertTo[ControlledBackgroundVariation]
      val illuminationDirectionRange = fields("illumination-angle").convertTo[ControlledIlluminationVariation]
      val poseVariation = fields("pose-range").convertTo[ControlledPoseVariation]
      val imageDimensions = fields("image-dimensions").convertTo[ImageDimensions]
      val defaultParameters = fields("default-parameters").convertTo[DefaultParameters]
      val landmarkTags = fields("landmark-tags").convertTo[IndexedSeq[String]]
      val occlusionMode = fields("occlusionMode").convertTo[String]


      new ControlledFacesSettings(
        outputLocation,
        backgrounds,
        renderingMethods,
        morphableModelParameters,
        imageDimensions,
        defaultParameters,
        landmarkTags,
        occlusionMode,
        illuminationDirectionRange,
        poseVariation,
        backgroundRange
      )
    }
  }

}

object RenderingMethodsJsonFormatV1 {
  import scalismo.faces.io.renderparameters.RenderParameterJSONFormatV4._

  val RenderingMethodsFormat: RootJsonFormat[RenderingMethods] = new RootJsonFormat[RenderingMethods] {
    override def write(obj: RenderingMethods): JsValue = {
      val contents = Map(
        "render" -> JsBoolean(obj.render),
        "render-depth" -> JsBoolean(obj.renderDepthMap),
        "render-color-correspondence-image" -> JsBoolean(obj.renderColorCorrespondenceImage),
        "render-normals" -> JsBoolean(obj.renderNormals),
        "render-albedo" -> JsBoolean(obj.renderAlbedo),
        "render-illumination" -> JsBoolean(obj.renderIllumination)
      )
      JsObject(contents)
    }

    override def read(json: JsValue): RenderingMethods = {
      val fields = json.asJsObject(s"expected RenderingMethods object, got: ${json}").fields
      RenderingMethods(
        render = fields("render").convertTo[Boolean],
        renderDepthMap = fields("render-depth").convertTo[Boolean],
        renderColorCorrespondenceImage = fields("render-color-correspondence-image").convertTo[Boolean],
        renderNormals = fields("render-normals").convertTo[Boolean],
        renderAlbedo = fields("render-albedo").convertTo[Boolean],
        renderIllumination = fields("render-illumination").convertTo[Boolean]
      )
    }
  }
}

object FacesSettingsJsonFormatV1 {

  import scalismo.faces.io.renderparameters.RenderParameterJSONFormatV4._

  class JsObjectOrdered(override val fields: ListMap[String, JsValue]) extends JsObject(fields)

  // version of the format and field name it is stored
  val version = "V1.0"
  val versionFieldName = "format-version"

  implicit val OutputLocationFormat: RootJsonFormat[OutputLocation] = new RootJsonFormat[OutputLocation] {
    override def write(obj: OutputLocation): JsValue = {
      JsObject(("output-directory", obj.outPath.toJson))
    }

    override def read(json: JsValue): OutputLocation = {
      val fields = json.asJsObject(s"expected OutputLocation object, got: $json").fields

      val outPath = fields("output-directory").convertTo[String]

      OutputLocation(outPath)
    }
  }

  implicit val BackgroundsFormat: RootJsonFormat[Backgrounds] = new RootJsonFormat[Backgrounds] {
    override def write(obj: Backgrounds): JsValue = {
      new JsObjectOrdered(ListMap(
        ("insert-background-image", obj.bg.toJson),
        ("background-images-directory", obj.bgPath.toJson),
        ("background-image-extension", obj.bgType.toJson)
      ))
    }

    override def read(json: JsValue): Backgrounds = {
      val fields = json.asJsObject(s"expected Backgounds object, got: $json").fields

      val bg = fields("insert-background-image").convertTo[Boolean]
      val bgPath = fields("background-images-directory").convertTo[String]
      val bgType = fields("background-image-extension").convertTo[String]

      Backgrounds(
        bgPath = bgPath,
        bg = bg,
        bgType = bgType)
    }
  }

  implicit val MorphableModelParametersFormat: RootJsonFormat[MorphableModelParameters] = new RootJsonFormat[MorphableModelParameters] {
    override def write(obj: MorphableModelParameters): JsValue = {
      new JsObjectOrdered(ListMap(
        ("number-of-ids-to-create", obj.nIds.toJson),
        ("number-of-samples-per-id", obj.nSamples.toJson),
        ("model-filename", obj.modelFn.toJson),
        ("dimension-of-shape-space", obj.nShape.toJson),
        ("dimension-of-color-space", obj.nColor.toJson),
        ("dimension-of-expression-space", obj.nExpression.toJson),
        ("add-expressions", obj.expressions.toJson)
      ))
    }

    override def read(json: JsValue): MorphableModelParameters = {
      val fields = json.asJsObject(s"expected MorphableModelParameters object, got: $json").fields

      val nIds = fields("number-of-ids-to-create").convertTo[Int]
      val nSamples = fields("number-of-samples-per-id").convertTo[Int]
      val nShape = fields("dimension-of-shape-space").convertTo[Int]
      val nColor = fields("dimension-of-color-space").convertTo[Int]
      val nExpression = fields("dimension-of-expression-space").convertTo[Int]
      val expressions = fields("add-expressions").convertTo[Boolean]
      val modelFn = fields("model-filename").convertTo[String]

      MorphableModelParameters(
        nIds = nIds,
        nSamples = nSamples,
        nShape = nShape,
        nColor = nColor,
        nExpression = nExpression,
        expressions = expressions,
        modelFn = modelFn
      )
    }
  }

  implicit val ImageDimensionsFormat: RootJsonFormat[ImageDimensions] = new RootJsonFormat[ImageDimensions] {
    override def write(obj: ImageDimensions): JsValue = {
      new JsObjectOrdered(ListMap(
        ("image-width", obj.imageWidth.toJson),
        ("image-height", obj.imageHeight.toJson)
      ))
    }

    override def read(json: JsValue): ImageDimensions = {
      val fields = json.asJsObject(s"expected ImageDimensions object, got: $json").fields

      val imageWidth = fields("image-width").convertTo[Int]
      val imageHeight = fields("image-height").convertTo[Int]

      ImageDimensions(
        imageWidth = imageWidth,
        imageHeight = imageHeight
      )
    }
  }


  implicit val DefaultParametersFormat: RootJsonFormat[DefaultParameters] = new RootJsonFormat[DefaultParameters] {
    override def write(obj: DefaultParameters): JsValue = {
      new JsObjectOrdered(ListMap(
        ("color-transform", obj.colorTransform.toJson),
        ("pose-transform", obj.pose.toJson),
        ("view-transform", obj.view.toJson),
        ("camera", obj.camera.toJson)
      ))
    }

    override def read(json: JsValue): DefaultParameters = {
      val fields = json.asJsObject(s"expected DefaultParameters object, got: $json").fields

      val pose = fields("pose-transform").convertTo[Pose]
      val view = fields("view-transform").convertTo[ViewParameter]
      val camera = fields("camera").convertTo[Camera]
      val colorTransform = fields("color-transform").convertTo[ColorTransform]

      DefaultParameters(
        pose = pose,
        view = view,
        camera = camera,
        colorTransform = colorTransform
      )
    }
  }


  implicit val distributionFormat: RootJsonFormat[Distribution] = new RootJsonFormat[Distribution] {
    override def write(distribution: Distribution): JsObject = {
      distribution match {
        case d: ConstantDistribution =>
          new JsObjectOrdered(ListMap(("type" -> "constant".toJson) +: d.toJson.asJsObject.fields.toList: _*))
        case d: GaussianDistribution =>
          new JsObjectOrdered(ListMap(("type" -> "gaussian".toJson) +: d.toJson.asJsObject.fields.toList: _*))
        case d: UniformDistribution =>
          new JsObjectOrdered(ListMap(("type" -> "uniform".toJson) +: d.toJson.asJsObject.fields.toList: _*))
        case d: MixtureDistribution =>
          new JsObjectOrdered(ListMap(("type" -> "mixture".toJson) +: d.toJson.asJsObject.fields.toList: _*))
      }
    }

    override def read(json: JsValue): Distribution = {
      val fields = json.asJsObject(s"expected Distribution object, got: $json").fields
      fields("type").convertTo[String] match {
        case "constant" => constantDistributionFormat.read(json)
        case "gaussian" => gaussianDistributionFormat.read(json)
        case "uniform" => uniformDistributionFormat.read(json)
        case "mixture" => mixtureDistributionFormat.read(json)
      }
    }
  }

  implicit val constantDistributionFormat: RootJsonFormat[ConstantDistribution] = new RootJsonFormat[ConstantDistribution] {
    override def write(distribution: ConstantDistribution): JsObject = {
      new JsObjectOrdered(ListMap(("value", distribution.value.toJson)))
    }

    override def read(json: JsValue): ConstantDistribution = {
      val fields = json.asJsObject(s"expected ConstantDistribution object, got: $json").fields
      val value = fields("value").convertTo[Double]
      ConstantDistribution(value)
    }
  }

  implicit val gaussianDistributionFormat: RootJsonFormat[GaussianDistribution] = new RootJsonFormat[GaussianDistribution] {
    override def write(distribution: GaussianDistribution): JsObject = {
      new JsObjectOrdered(ListMap(
        ("mean", distribution.mean.toJson),
        ("variance", distribution.variance.toJson)
      ))
    }

    override def read(json: JsValue): GaussianDistribution = {
      val fields = json.asJsObject(s"expected GaussianDistribution object, got: $json").fields
      val mean = fields("mean").convertTo[Double]
      val variance = fields("variance").convertTo[Double]
      GaussianDistribution(mean, variance)
    }
  }

  implicit val uniformDistributionFormat: RootJsonFormat[UniformDistribution] = new RootJsonFormat[UniformDistribution] {
    override def write(distribution: UniformDistribution): JsObject = {
      new JsObjectOrdered(ListMap(
        ("lower", distribution.lower.toJson),
        ("higher", distribution.higher.toJson)
      ))
    }

    override def read(json: JsValue): UniformDistribution = {
      val fields = json.asJsObject(s"expected UniformDistribution object, got: $json").fields
      val lower = fields("lower").convertTo[Double]
      val higher = fields("higher").convertTo[Double]
      UniformDistribution(lower, higher)
    }
  }

  implicit val mixtureDistributionFormat: RootJsonFormat[MixtureDistribution] = new RootJsonFormat[MixtureDistribution] {
    override def write(distribution: MixtureDistribution): JsObject = {
      new JsObjectOrdered(ListMap(
        ("components", distribution.weightedDistributions.toJson)
      ))
    }

    override def read(json: JsValue): MixtureDistribution = {
      val fields = json.asJsObject(s"expected MixtureDistribution object, got: $json").fields
      val weightedComponents = fields("components").convertTo[List[(Double, Distribution)]]
      MixtureDistribution(weightedComponents)
    }
  }
}
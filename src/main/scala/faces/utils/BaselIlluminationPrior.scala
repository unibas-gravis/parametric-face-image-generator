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

import java.io.File

import scalismo.faces.io.RenderParameterIO
import scalismo.faces.parameters.SphericalHarmonicsLight
import scalismo.statisticalmodel.MultivariateNormalDistribution
import scalismo.utils.Random

case class BaselIlluminationPrior(dir: String){
  require(new File(dir).exists(), "Illumination Prior path does not exist")

  // search all parameter files to estimate illumination
  lazy val files = {
    val listFiles = new File(dir).listFiles.filter(_.getName.endsWith(".rps")).toIndexedSeq
    listFiles
  }

  // load files holding illumination parameters (empirical distribution)
  lazy val allIllumination = files.map(f => {
    val rps = RenderParameterIO.read(f).get
    rps.environmentMap
  })

  // load spherical harmonics into a vectorized representation
  lazy val allIlluminationData = allIllumination.map(i => i.toBreezeVector)

  //calculate multinormaldistribution of illumination data
  lazy val mnd = MultivariateNormalDistribution.estimateFromData(allIlluminationData)

  // generates a random Illumination condition following the empirical distribution on the Basel Illumination Prior 2017 data
  def rndEmpirical (implicit rnd: Random) : SphericalHarmonicsLight = {
    allIllumination(rnd.scalaRandom.nextInt(allIllumination.length))
  }

  // generates a random Illumination condition following the a multivariate normal distribution on the Basel Illumination Prior 2017 data
  def rndMND (implicit rnd: Random): SphericalHarmonicsLight = {
    val sample = mnd.sample()
    SphericalHarmonicsLight.fromBreezeVector(sample)
  }

  // choose a an illumination based on defined distribution
  def rnd(illumination: String)(implicit rnd: Random): SphericalHarmonicsLight = {
    illumination match{
      case "staticFrontal" => SphericalHarmonicsLight.frontal.withNumberOfBands(2)
      case "empirical" => rndEmpirical
      case "multiVariateNormal" => rndMND
      case _ =>  throw new Exception("please choose a valid illumination setting")
    }
  }

}

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

import scalismo.utils.Random

sealed trait Distribution {
  def apply()(implicit rnd: Random): Double
}


case class ConstantDistribution(value: Double) extends Distribution {
  override def apply()(implicit rnd: Random): Double = value
}

case class GaussianDistribution(mean: Double, variance: Double) extends Distribution {
  override def apply()(implicit rnd: Random): Double = {
    rnd.scalaRandom.nextGaussian()*variance+mean
  }
}

case class UniformDistribution(lower: Double, higher: Double) extends Distribution {
  override def apply()(implicit rnd: Random): Double = {
    rnd.scalaRandom.nextDouble()*(higher-lower)+lower
  }
}

case class MixtureDistribution( weightedDistributions: Seq[(Double,Distribution)]) extends Distribution {

  val summedWeights: IndexedSeq[Double] = {
    val summed = weightedDistributions.map(_._1).scanLeft(0.0)(_+_)
    summed.map(_ / summed.last).toIndexedSeq.tail
  }

  override def apply()(implicit rnd: Random): Double = {
    val distributionIndex = summedWeights.indexWhere(_ > rnd.scalaRandom.nextDouble())
    weightedDistributions(distributionIndex)._2 ()
  }

}
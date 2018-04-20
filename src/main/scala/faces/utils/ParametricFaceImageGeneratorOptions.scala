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

import org.rogach.scallop.{ScallopConf, ScallopOption}
import org.rogach.scallop.exceptions.ScallopException


class ParametricFaceImageGeneratorOptions(args: Seq[String]) extends ScallopConf(args) {
  banner(
    """|parametric-face-image-generator
       |© University of Basel
       |License: http://www.apache.org/licenses/LICENSE-2.0
       |
       |Options:""".stripMargin)

  val configurationFile: ScallopOption[String] = opt[String](required = true,descr = "configuration file with the parameters")

  footer(
    """""".stripMargin
  )

  override def onError(e: Throwable): Unit = e match {
    case ScallopException(message) =>
      printHelp
      println("You provided the arguments: "+args.mkString(" "))
      println(message)
      sys.exit(1)
    case ex => super.onError(ex)
  }
}
/*
 * Copyright 2020 Google LLC
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

package models

import java.net.URL
import java.util.UUID

import models.Arena.ArenaState
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpec}
import play.api.test.Helpers._

import scala.concurrent.Future
import scala.concurrent.duration._


class DataSpec extends WordSpec with MustMatchers with BeforeAndAfterAll {

  implicit val ec = scala.concurrent.ExecutionContext.global

  "wasHit" must {
    "get sent to services" in {

      val player = Player("http://foo", "foo", new URL("http://foo"))
      val playerState = PlayerState(0, 0, Direction.S, true, 0)
      val initState = ArenaState("test", "test", "test", Set.empty[UUID], Set(player), Map(player.service -> playerState))

      // if wasHit was true, then move forward, otherwise do nothing
      val newState = await {
        Arena.updateArena(initState) { (state, _) =>
          Future.successful {
            if (state(player.service).wasHit) {
              Some(Forward -> Duration.Zero)
            }
            else {
              None
            }
          }
        }
      }

      newState.playerStates(player.service).y must equal (1)
    }

    "be accurate" in {
      val player1 = Player("http://foo", "foo", new URL("http://foo"))
      val player2 = Player("http://bar", "bar", new URL("http://bar"))
      val player1State = PlayerState(0, 0, Direction.S, true, 0)
      val player2State = PlayerState(0, 1, Direction.N, false, 0)

      val initState = ArenaState("test", "test", "test", Set.empty[UUID], Set(player1, player2), Map(player1.service -> player1State, player2.service -> player2State))

      // if wasHit was true, then move forward, otherwise do nothing
      val newState = await {
        Arena.updateArena(initState) { (_, player) =>
          Future.successful {
            if (player == player1) {
              Some(Throw -> Duration.Zero)
            }
            else {
              None
            }
          }
        }
      }

      newState.playerStates(player1.service).wasHit must equal (false)
      newState.playerStates(player2.service).wasHit must equal (true)
    }
  }

  "two players throwing at each other" must {
    "only award the player with the lowest latency" in {
      val player1 = Player("http://foo", "foo", new URL("http://foo"))
      val player2 = Player("http://bar", "bar", new URL("http://bar"))
      val player1State = PlayerState(0, 0, Direction.S, false, 0)
      val player2State = PlayerState(0, 1, Direction.N, false, 0)

      val initState = ArenaState("test", "test", "test", Set.empty[UUID], Set(player1, player2), Map(player1.service -> player1State, player2.service -> player2State))

      // if wasHit was true, then move forward, otherwise do nothing
      val newState = await {
        Arena.updateArena(initState) { (_, player) =>
          Future.successful {
            val latency = if (player == player1) {
              1.seconds
            }
            else {
              2.seconds
            }

            Some(Throw -> latency)
          }
        }
      }

      newState.playerStates(player1.service).score must equal (1)
      newState.playerStates(player1.service).wasHit must equal (false)
      newState.playerStates(player2.service).score must equal (-1)
      newState.playerStates(player2.service).wasHit must equal (true)
    }
  }

  "a player throwing" must {
    "must not hit more than one other player" in {
      val player1 = Player("http://foo", "foo", new URL("http://foo"))
      val player2 = Player("http://bar", "bar", new URL("http://bar"))
      val player3 = Player("http://baz", "baz", new URL("http://baz"))
      val player1State = PlayerState(0, 0, Direction.S, false, 0)
      val player2State = PlayerState(0, 1, Direction.N, false, 0)
      val player3State = PlayerState(0, 2, Direction.N, false, 0)

      val playerStates = Map(player1.service -> player1State, player2.service -> player2State, player3.service -> player3State)

      val initState = ArenaState("test", "test", "test", Set.empty[UUID], Set(player1, player2, player3), playerStates)

      // if wasHit was true, then move forward, otherwise do nothing
      val newState = await {
        Arena.updateArena(initState) { (_, player) =>
          Future.successful {
            if (player == player1) {
              Some(Throw -> Duration.Zero)
            }
            else {
              None
            }
          }
        }
      }

      newState.playerStates(player1.service).wasHit must equal (false)
      newState.playerStates(player2.service).wasHit must equal (true)
      newState.playerStates(player3.service).wasHit must equal (false)
    }
  }

}

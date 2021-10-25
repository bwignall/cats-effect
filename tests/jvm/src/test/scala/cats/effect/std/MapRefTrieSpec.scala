/*
 * Copyright 2020-2021 Typelevel
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

package cats.effect.std

import cats._
import cats.implicits._
import cats.data.State
import cats.effect._

import scala.concurrent.duration._

class MapRefTrieSpec extends BaseSpec {

  private val smallDelay: IO[Unit] = IO.sleep(20.millis)
  private def awaitEqual[A: Eq](t: IO[A], success: A): IO[Unit] =
    t.flatMap(a => if (Eq[A].eqv(a, success)) IO.unit else smallDelay *> awaitEqual(t, success))

  "MapRef TrieMap" should {

    "MapRef.ofScalaConcurrentTrieMap - concurrent modifications" in real {
      val finalValue = 100
      val r = MapRef.inScalaConcurrentTrieMap[SyncIO, IO, Unit, Int].unsafeRunSync()
      val modifies = List.fill(finalValue)(r(()).update(_.map(_ + 1))).parSequence
      val test = r(()).set(Some(0)) *> modifies.start *> awaitEqual(r(()).get, finalValue.some)
      test.map(_ => ok)
    }

    "MapRef.ofScalaConcurrentTrieMap - getAndSet - successful" in real {
      val op = for {
        r <- MapRef.ofScalaConcurrentTrieMap[IO, Unit, Int]
        _ <- r(()).set(Some(0))
        getAndSetResult <- r(()).getAndSet(Some(1))
        getResult <- r(()).get
      } yield getAndSetResult == Some(0) && getResult == Some(1)

      op.map(a => a must_=== true)
    }

    "MapRef.ofScalaConcurrentTrieMap - access - successful" in real {
      val op = for {
        r <- MapRef.ofScalaConcurrentTrieMap[IO, Unit, Int]
        _ <- r(()).set(Some(0))
        accessed <- r(()).access
        (value, setter) = accessed
        success <- setter(value.map(_ + 1))
        result <- r(()).get
      } yield success && result == Some(1)

      op.map(a => a must_=== true)
    }

    "MapRef.ofScalaConcurrentTrieMap - access - setter should fail if value is modified before setter is called with None/Some" in real {
      val op = for {
        r <- MapRef.ofScalaConcurrentTrieMap[IO, Unit, Int]
        accessed <- r(()).access
        (value, setter) = accessed
        _ <- r(()).set(Some(5))
        success <- setter(value.map(_ + 1))
        result <- r(()).get
      } yield !success && result == Some(5)

      op.map(a => a must_=== true)
    }

    "MapRef.ofScalaConcurrentTrieMap - access - setter should fail if value is modified before setter is called with init Some/Some" in real {
      val op = for {
        r <- MapRef.ofScalaConcurrentTrieMap[IO, Unit, Int]
        _ <- r(()).set(Some(0))
        accessed <- r(()).access
        (value, setter) = accessed
        _ <- r(()).set(Some(5))
        success <- setter(value.map(_ + 1))
        result <- r(()).get
      } yield !success && result == Some(5)

      op.map(a => a must_=== true)
    }

    "MapRef.ofScalaConcurrentTrieMap - access - setter should fail if value is modified before setter is called with init Some/None" in real {
      val op = for {
        r <- MapRef.ofScalaConcurrentTrieMap[IO, Unit, Int]
        _ <- r(()).set(Some(0))
        accessed <- r(()).access
        (value, setter) = accessed
        _ <- r(()).set(Some(5))
        success <- setter(None)
        result <- r(()).get
      } yield !success && result == Some(5)

      op.map(a => a must_=== true)
    }

    "MapRef.ofScalaConcurrentTrieMap - access - setter should fail if called twice" in real {
      val op = for {
        r <- MapRef.ofScalaConcurrentTrieMap[IO, Unit, Int]
        _ <- r(()).set(Some(0))
        accessed <- r(()).access
        (value, setter) = accessed
        cond1 <- setter(value.map(_ + 1))
        _ <- r(()).set(value)
        cond2 <- setter(None)
        result <- r(()).get
      } yield cond1 && !cond2 && result == Some(0)

      op.map(a => a must_=== true)
    }

    "MapRef.ofScalaConcurrentTrieMap - tryUpdate - modification occurs successfully" in real {
      val op = for {
        r <- MapRef.ofScalaConcurrentTrieMap[IO, Unit, Int]
        _ <- r(()).set(Some(0))
        result <- r(()).tryUpdate(_.map(_ + 1))
        value <- r(()).get
      } yield result && value == Some(1)

      op.map(a => a must_=== true)
    }

    "MapRef.ofScalaConcurrentTrieMap - tryUpdate - should fail to update if modification has occurred" in real {
      import cats.effect.unsafe.implicits.global
      val updateRefUnsafely: Ref[IO, Option[Int]] => Unit =
        _.update(_.map(_ + 1)).unsafeRunSync()

      val op = for {
        r <- MapRef.ofScalaConcurrentTrieMap[IO, Unit, Int]
        _ <- r(()).set(Some(0))
        result <- r(()).tryUpdate(currentValue => {
          updateRefUnsafely(r(()))
          currentValue.map(_ + 1)
        })
      } yield result

      op.map(a => a must_=== false)
    }

    "MapRef.ofScalaConcurrentTrieMap - tryModifyState - modification occurs successfully" in real {
      val op = for {
        r <- MapRef.ofScalaConcurrentTrieMap[IO, Unit, Int]
        _ <- r(()).set(Some(0))
        result <- r(()).tryModifyState(State.pure(Some(1)))
      } yield result.contains(Some(1))

      op.map(a => a must_=== true)
    }

    "MapRef.ofScalaConcurrentTrieMap - modifyState - modification occurs successfully" in real {
      val op = for {
        r <- MapRef.ofScalaConcurrentTrieMap[IO, Unit, Int]
        _ <- r(()).set(Some(0))
        result <- r(()).modifyState(State.pure(Some(1)))
      } yield result == Some(1)

      op.map(a => a must_=== true)
    }

  }

}

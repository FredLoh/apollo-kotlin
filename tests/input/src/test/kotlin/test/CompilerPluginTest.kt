package test

import com.apollographql.apollo3.annotations.codegen.ApolloCodegenInputObject
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.api.assertOneOf
import com.example.CreateUser2Query
import com.example.CreateUserQuery
import com.example.type.FindUserByFriendInput
import com.example.type.FindUserBySocialNetworkInput
import com.example.type.UserInput
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Copied from the codegen because for now it doesn't add the @ApolloCodegenInputObject annotation
@ApolloCodegenInputObject("FindUserInput", isOneOf = true)
public data class FindUserInput(
    public val email: Optional<String?> = Optional.Absent,
    public val name: Optional<String?> = Optional.Absent,
    public val identity: Optional<FindUserBySocialNetworkInput?> = Optional.Absent,
    public val friends: Optional<FindUserByFriendInput?> = Optional.Absent,
) {
  init {
    assertOneOf(email, name, identity, friends)
  }

  public class Builder {
    private var email: Optional<String?> = Optional.Absent

    private var name: Optional<String?> = Optional.Absent

    private var identity: Optional<FindUserBySocialNetworkInput?> = Optional.Absent

    private var friends: Optional<FindUserByFriendInput?> = Optional.Absent

    public fun email(email: String?): Builder {
      this.email = Optional.Present(email)
      return this
    }

    public fun name(name: String?): Builder {
      this.name = Optional.Present(name)
      return this
    }

    public fun identity(identity: FindUserBySocialNetworkInput?): Builder {
      this.identity = Optional.Present(identity)
      return this
    }

    public fun friends(friends: FindUserByFriendInput?): Builder {
      this.friends = Optional.Present(friends)
      return this
    }

    public fun build(): FindUserInput = FindUserInput(
        email = email,
        name = name,
        identity = identity,
        friends = friends,
    )
  }
}


class CompilerPluginTest {
  @Test
  fun simple() {
    val input = UserInput.Builder()
        .name("Test User")
        .email("test@example.com")
        .build()

    assertEquals("Test User", input.name)
    assertEquals(Optional.present("test@example.com"), input.email)

    val query1 = CreateUserQuery.Builder()
        .input(input)
        .build()
    assertEquals("Test User", query1.input.name)

    val query2 = CreateUser2Query.Builder()
        .input(input)
        .build()
    assertEquals("Test User", query2.input?.name)
  }

  @Test
  fun oneOfWithConstructor() {
    FindUserInput(email = Optional.present("test@example.com"))

    var e = assertFailsWith<IllegalArgumentException> {
      FindUserInput(
          email = Optional.present("test@example.com"),
          name = Optional.present("Test User"),
      )
    }
    assertEquals("@oneOf input must have one field set (got 2)", e.message)

    e = assertFailsWith<IllegalArgumentException> {
      FindUserInput()
    }
    assertEquals("@oneOf input must have one field set (got 0)", e.message)

    e = assertFailsWith<IllegalArgumentException> {
      FindUserInput(email = Optional.present(null))
    }
    assertEquals("The value set on @oneOf input field must be non-null", e.message)

    e = assertFailsWith<IllegalArgumentException> {
      FindUserInput(
          email = Optional.present(null),
          name = Optional.present("Test User")
      )
    }
    assertEquals("@oneOf input must have one field set (got 2)", e.message)
  }

  @Test
  fun oneOfWithBuilder() {
    FindUserInput.Builder()
        .email("test@example.com")
        .build()

    var e = assertFailsWith<IllegalArgumentException> {
      FindUserInput.Builder()
          .email("test@example.com")
          .name("Test User")
          .build()
    }
    assertEquals("@oneOf input must have one field set (got 2)", e.message)

    e = assertFailsWith<IllegalArgumentException> {
      FindUserInput.Builder()
          .build()
    }
    assertEquals("@oneOf input must have one field set (got 0)", e.message)

    e = assertFailsWith<IllegalArgumentException> {
      FindUserInput.Builder()
          .email(null)
          .build()
    }
    assertEquals("The value set on @oneOf input field must be non-null", e.message)

    e = assertFailsWith<IllegalArgumentException> {
      FindUserInput.Builder()
          .email(null)
          .name("Test User")
          .build()
    }
    assertEquals("@oneOf input must have one field set (got 2)", e.message)
  }
}

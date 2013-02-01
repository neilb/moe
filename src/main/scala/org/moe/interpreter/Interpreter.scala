package org.moe.interpreter

import org.moe.runtime._
import org.moe.ast._

import scala.collection.mutable.HashMap

object Interpreter {

  val stub = new MoeObject()

  object Utils {
    def objToNumeric(obj: MoeObject) = obj match {
      case i: MoeIntObject => i.getNativeValue.toDouble
      case n: MoeFloatObject => n.getNativeValue
    }

    def inNewEnv[T](env: MoeEnvironment)(body: MoeEnvironment => T): T = {
      val newEnv = new MoeEnvironment(Some(env))

      body(env)
    }
  }

  def eval(env: MoeEnvironment, node: AST): MoeObject = {
    val scoped = Utils.inNewEnv[MoeObject](env) _
    node match {

      // containers

      case CompilationUnitNode(body) => eval(env, body)
      case ScopeNode(body) => eval(new MoeEnvironment(Some(env)), body)
      case StatementsNode(nodes) => {

        // foldLeft iterates over each node (left to right) in the list, executing
        // a function.  That function is given two arguments: the result of the
        // previous iteration and the next item in the list.  It returns the result
        // of the final iteration. Many times it used to accumulate, such as
        // finding a sum of a list.  In thise case we don't acculate, we just
        // return the result of each eval.  Therefore the final result will be
        // the result of the last eval.
        nodes.foldLeft[MoeObject](MoeRuntime.NativeObjects.getUndef)(
          (_, node) => eval(env, node)
        )
      }

      // literals

      case IntLiteralNode(value)     => MoeRuntime.NativeObjects.getInt(value)
      case FloatLiteralNode(value)   => MoeRuntime.NativeObjects.getFloat(value)
      case StringLiteralNode(value)  => MoeRuntime.NativeObjects.getString(value)
      case BooleanLiteralNode(value) => MoeRuntime.NativeObjects.getBool(value)

      case UndefLiteralNode() => MoeRuntime.NativeObjects.getUndef
      case SelfLiteralNode()  => env.getCurrentInvocant
      case ClassLiteralNode() => env.getCurrentClass
      case SuperLiteralNode() => {
        val klass = env.getCurrentClass
        klass.getSuperclass.getOrElse(
          throw new MoeRuntime.Errors.SuperclassNotFound(klass.getName)
        )
      }

      case ArrayLiteralNode(values) => {
        val array: List[MoeObject] = values.map((i) => eval(env, i))
        MoeRuntime.NativeObjects.getArray(array)
      }

      case PairLiteralNode(key, value) => MoeRuntime.NativeObjects.getPair(eval(env, key) -> eval(env, value))

      case HashLiteralNode(map) => {
        // NOTE:
        // forcing each element to become
        // a single MoePairObject might be
        // a little too restrictive, it
        // should (in theory) be possible
        // for it to evaluate into many
        // MoePairObject instances as well
        // - SL
        MoeRuntime.NativeObjects.getHash(
          map.map(
            pair => eval(env, pair)
            .asInstanceOf[MoePairObject].getNativeValue
          ).toMap
        )
      }

      // unary operators

      case IncrementNode(receiver: AST) => receiver match {
        case VariableAccessNode(varName) => env.get(varName) match {
          case i: MoeIntObject => {
            env.set(varName, MoeRuntime.NativeObjects.getInt(i.getNativeValue + 1))
            env.get(varName)
          }
          case n: MoeFloatObject => {
            env.set(varName, MoeRuntime.NativeObjects.getFloat(n.getNativeValue + 1.0))
            env.get(varName)
          }
        }
      }
      case DecrementNode(receiver: AST) => receiver match {
        case VariableAccessNode(varName) => env.get(varName) match {
          case i: MoeIntObject => {
            env.set(varName, MoeRuntime.NativeObjects.getInt(i.getNativeValue - 1))
            env.get(varName)
          }
          case n: MoeFloatObject => {
            env.set(varName, MoeRuntime.NativeObjects.getFloat(n.getNativeValue - 1.0))
            env.get(varName)
          }
        }
      }

      case NotNode(receiver) => {
        if(eval(env, receiver).isTrue) {
          MoeRuntime.NativeObjects.getFalse
        } else {
          MoeRuntime.NativeObjects.getTrue
        }
      }

      // binary operators

      case AndNode(lhs, rhs) => {
        val left_result = eval(env, lhs)
        if(left_result.isTrue) {
          eval(env, rhs)
        } else {
          left_result
        }
      }

      case OrNode(lhs, rhs) => {
        val left_result = eval(env, lhs)
        if(left_result.isTrue) {
          left_result
        } else {
          eval(env, rhs)
        }
      }

      case LessThanNode(lhs, rhs) => {
        val lhs_result: Double = Utils.objToNumeric(eval(env, lhs))
        val rhs_result: Double = Utils.objToNumeric(eval(env, rhs))

        val result = lhs_result < rhs_result
        MoeRuntime.NativeObjects.getBool(result)
      }

      case GreaterThanNode(lhs, rhs) => {
        val lhs_result: Double = Utils.objToNumeric(eval(env, lhs))
        val rhs_result: Double = Utils.objToNumeric(eval(env, rhs))

        val result = lhs_result > rhs_result
        MoeRuntime.NativeObjects.getBool(result)
      }

      // value lookup, assignment and declaration

      case ClassAccessNode(name) => stub
      case ClassDeclarationNode(name, superclass, body) => stub

      case PackageDeclarationNode(name, body) => {
        scoped { newEnv =>
          val parent = env.getCurrentPackage
          val pkg    = new MoePackage(name, newEnv)
          parent.addSubPackage(pkg)
          newEnv.setCurrentPackage(pkg)
          eval(newEnv, body)
        }
      }

      case ConstructorDeclarationNode(params, body) => stub
      case DestructorDeclarationNode(params, body) => stub

      case MethodDeclarationNode(name, params, body) => stub
      // TODO: handle arguments
      case SubroutineDeclarationNode(name, params, body) => {
        scoped { newEnv =>
          val sub = new MoeSubroutine(
            name,
            params => eval(newEnv, body)
          )
          env.getCurrentPackage.addSubroutine( sub )
          sub
        }
      }

      case AttributeAccessNode(name) => stub
      case AttributeAssignmentNode(name, expression) => stub
      case AttributeDeclarationNode(name, expression) => stub

      // TODO context etc
      case VariableAccessNode(name) => env.get(name)
      case VariableAssignmentNode(name, expression) => {
        env.set(name, eval(env, expression))
        env.get(name)
      }
      case VariableDeclarationNode(name, expression) => {
        env.create(name, eval(env, expression))
        env.get(name)
      }

      // operations

      case MethodCallNode(invocant, method_name, args) => stub
      case SubroutineCallNode(function_name, args) => {
        val sub = env.getCurrentPackage.getSubroutine(function_name).getOrElse(
            throw new MoeRuntime.Errors.SubroutineNotFound(function_name)
        )
        // NOTE:
        // I think we might need to eval these
        // in the context of the sub body, and
        // we also need to make sure that we 
        // add the args to the environment as 
        // well.
        // - SL
        sub.execute(args.map(eval(env, _)))
      }

      // statements

      case IfNode(if_condition, if_body) => {
        eval(env,
          IfElseNode(
            if_condition,
            if_body,
            UndefLiteralNode()
          )
        )
      }

      case IfElseNode(if_condition, if_body, else_body) => {
        if (eval(env, if_condition).isTrue) {
          eval(env, if_body)
        } else {
          eval(env, else_body)
        }
      }

      case IfElsifNode(if_condition, if_body, elsif_condition, elsif_body) => {
        eval( env,
          IfElseNode(
            if_condition,
            if_body,
            IfNode(
              elsif_condition,
              elsif_body
            )
          )
        )
      }

      case IfElsifElseNode(if_condition, if_body, elsif_condition, elsif_body, else_body) => {
        eval(env,
          IfElseNode(
            if_condition,
            if_body,
            IfElseNode(
              elsif_condition,
              elsif_body,
              else_body
            )
          )
        )
      }

      case UnlessNode(unless_condition, unless_body) => {
        eval(env,
          UnlessElseNode(
            unless_condition,
            unless_body,
            UndefLiteralNode()
          )
        )
      }
      case UnlessElseNode(unless_condition, unless_body, else_body) => {
        eval(env,
          IfElseNode(
            NotNode(unless_condition),
            unless_body,
            else_body
          )
        )
      }

      case TryNode(body, catch_nodes, finally_nodes) => stub
      case CatchNode(type_name, local_name, body) => stub
      case FinallyNode(body) => stub

      case WhileNode(condition, body) => {
        scoped { newEnv =>
          while (eval(newEnv, condition).isTrue) {
            eval(newEnv, body)
          }
          MoeRuntime.NativeObjects.getUndef // XXX
        }
      }

      case DoWhileNode(condition, body) => {
        scoped { newEnv =>
          do {
            eval(newEnv, body)
          } while (eval(newEnv, condition).isTrue)
          MoeRuntime.NativeObjects.getUndef // XXX
        }
      }

      case ForeachNode(topic, list, body) => {
        eval(env, list) match {
          case objects: MoeArrayObject => {
            val applyScopeInjection = { 
              (
                newEnv: MoeEnvironment, 
                name: String, 
                obj: MoeObject, 
                f: (MoeEnvironment, String, MoeObject) => Any
              ) =>
              f(env, name, obj)
              eval(newEnv, body)
            }
            scoped { newEnv =>
              for (o <- objects.getNativeValue)
                topic match {
                  // XXX ran into issues trying to eval(env, ScopeNode(...))
                  // since o is already evaluated at this point
                  case VariableDeclarationNode(name, expr) =>
                    applyScopeInjection(newEnv, name, o, (_.create(_, _)))
                  // Don't do anything special here, env access will just walk back
                  case VariableAccessNode(name) =>
                    applyScopeInjection(newEnv, name, o, (_.set(_, _)))
                }
              MoeRuntime.NativeObjects.getUndef // XXX
            }
          }
        }
      }
      case ForNode(init, condition, update, body) => {
        scoped(
          newEnv => {
            eval(newEnv, init)
            while (eval(newEnv, condition).isTrue) {
              eval(newEnv, body)
              eval(newEnv, update)
            }
            MoeRuntime.NativeObjects.getUndef
          }
        )

      }
      case _ => throw new MoeRuntime.Errors.UnknownNode("Unknown Node")
    }
  }

}

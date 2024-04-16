package ucc.alvarium

import com.alvarium.contracts.AnnotationType
import com.alvarium.utils.{ImmutablePropertyBag, PropertyBag}
import java.util 

object PropertyBag {
  def apply(properties: (AnnotationType, AnyRef)*): PropertyBag = {
    val map = new util.HashMap[String, AnyRef]()
    for ((k, v) <- properties)
      map.put(k.toString, v)

    new ImmutablePropertyBag(map)
  }
}
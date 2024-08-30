package nl.qunit.bpmnmeister.engine.mapping;

import java.lang.reflect.InvocationTargetException;
import org.mapstruct.Mapper;
import org.mapstruct.ObjectFactory;
import org.mapstruct.SubclassMapping;
import org.mapstruct.TargetType;

@Mapper
public interface MyMapper {
  @SubclassMapping(source = SubClassADTO.class, target = SubClassA.class)
  @SubclassMapping(source = SubClassBDTO.class, target = SubClassB.class)
  BaseClass map(BaseClassDTO baseElement2);

  @ObjectFactory
  default <T extends BaseClass> T resolveEquipment(
      BaseClassDTO sourceDto, @TargetType Class<T> type) {
    return getNewInstance(type);
  }

  // NOSONAR
  private static <T> T getNewInstance(Class<T> type) {
    try {
      return type.getDeclaredConstructor().newInstance();
    } catch (InstantiationException
        | IllegalAccessException
        | NoSuchMethodException
        | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }
  //  SubClassA map(SubClassADTO subElementA);
  //  SubClassB map(SubClassBDTO subElementB);
}

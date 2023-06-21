package com.flipkart.krystal.vajram.codegen.models;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.flipkart.krystal.vajram.VajramID;
import com.flipkart.krystal.vajram.das.DataAccessSpec;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("vajram")
public final class VajramDependencyDef extends DependencyDef {
  private String vajramClass;

  // 'true' for backward compatibility - this was the default behaviour before this field was
  // introduced. At some point in the future, this should change to default to false.
  private boolean canFanout = true;

  @Override
  public DataAccessSpec toDataAccessSpec() {
    return VajramID.fromClass(vajramClass);
  }

  @Override
  public boolean canFanout() {
    return canFanout;
  }
}

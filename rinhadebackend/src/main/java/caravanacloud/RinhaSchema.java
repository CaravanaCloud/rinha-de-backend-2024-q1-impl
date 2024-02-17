package caravanacloud;

import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.annotations.AutoProtoSchemaBuilder;

@AutoProtoSchemaBuilder(includeClasses = {Cliente.class, Transacao.class})
public interface RinhaSchema extends GeneratedSchema {
}
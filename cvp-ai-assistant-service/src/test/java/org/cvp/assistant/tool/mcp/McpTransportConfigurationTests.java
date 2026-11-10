package org.cvp.assistant.tool.mcp;
import org.cvp.assistant.tool.TypedToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(properties={"assistant.tools.transport=mcp","assistant.tools.mcp-base-url=http://127.0.0.1:65534/mcp"})
class McpTransportConfigurationTests { @Autowired TypedToolRegistry registry; @Test void mcpModeRegistersOnlyAllowlistedBusinessTools(){assertThat(registry.definitions()).extracting(d->d.name()).containsExactlyInAnyOrder("getMaintenanceStatus","getVehicleMaintenanceState","getVehicleCapability");} }

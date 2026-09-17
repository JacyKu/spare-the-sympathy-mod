package sts.mod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shared {@code /sts} root: every command class registers its own
 * {@code sts} literal and Brigadier merges the trees. If a registration order
 * or merge change ever dropped a child (e.g. {@code link}), the command would
 * silently fall through to the server and do nothing.
 */
class CommandTreeTest {
	private static void register(Class<?> owner, CommandDispatcher<FabricClientCommandSource> dispatcher) throws Exception {
		Method method = null;
		for (Method candidate : owner.getDeclaredMethods()) {
			if (candidate.getName().equals("registerCommand") && candidate.getParameterCount() == 2) {
				method = candidate;
				break;
			}
		}
		assertNotNull(method, owner.getSimpleName() + " exposes a registerCommand(dispatcher, context) method");
		method.setAccessible(true);
		method.invoke(null, dispatcher, (CommandBuildContext) null);
	}

	@Test
	void stsRootKeepsEverySubcommand() throws Exception {
		CommandDispatcher<FabricClientCommandSource> dispatcher = new CommandDispatcher<>();
		// Same order the client initializer uses.
		register(DumpCommand.class, dispatcher);
		register(LinkCommand.class, dispatcher);
		register(ConfigCommand.class, dispatcher);

		CommandNode<FabricClientCommandSource> sts = dispatcher.getRoot().getChild("sts");
		assertTrue(sts != null, "/sts root registered");
		for (String name : new String[] { "dump", "help", "link", "upload_item", "export_build", "upload_build", "config" }) {
			assertTrue(sts.getChild(name) != null, "/sts " + name + " registered");
		}
	}
}

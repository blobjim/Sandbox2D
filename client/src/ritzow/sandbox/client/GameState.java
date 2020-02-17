package ritzow.sandbox.client;

import ritzow.sandbox.client.graphics.Display;
import ritzow.sandbox.client.graphics.ModelRenderProgram;

class GameState {
	ModelRenderProgram shader;
	Display display;
	long cursorPick, cursorMallet;
	MainMenuContext menuContext;
}

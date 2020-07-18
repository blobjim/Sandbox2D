package ritzow.sandbox.client.ui.element;

import ritzow.sandbox.client.ui.GuiElement;
import ritzow.sandbox.client.ui.GuiRenderer;
import ritzow.sandbox.client.ui.Shape;

public record RotationAnimation(GuiElement child, double radiansPerNano) implements GuiElement {
	@Override
	public void render(GuiRenderer renderer, long nanos) {
		renderer.draw(child, 1, 0, 0, 1, 1, (float)(System.nanoTime() * radiansPerNano));
	}

	@Override
	public Shape shape() {
		return child.shape();
	}
}

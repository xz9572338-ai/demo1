import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { App } from "./App";

describe("App", () => {
  it("通过可访问标题呈现项目基线", () => {
    render(<App />);
    expect(screen.getByRole("heading", { level: 1, name: "项目基线已就绪" })).toBeInTheDocument();
  });
});

import { describe, it, expect } from "vitest";
import { cn } from "./cn";

describe("cn", () => {
  it("joins multiple class strings", () => {
    expect(cn("a", "b")).toBe("a b");
  });

  it("drops falsy values", () => {
    expect(cn("a", false, null, undefined, "b")).toBe("a b");
  });

  it("applies conditional object syntax from clsx", () => {
    expect(cn("base", { active: true, disabled: false })).toBe("base active");
  });

  it("de-duplicates conflicting tailwind classes, keeping the last", () => {
    // tailwind-merge resolves px-2 vs px-4 to the latter
    expect(cn("px-2", "px-4")).toBe("px-4");
    expect(cn("text-sm", "text-lg")).toBe("text-lg");
  });
});

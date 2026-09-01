-- Redstone disco: every redstone lamp in the patch follows its block's
-- redstone state, and a lever on fixture 1 toggles a party mode.

party = false

minelight.on("redstone.on", function(e)
  minelight.intensity(e.fixtureId, 255)
  if e.fixtureId == 1 then
    party = not party
    if party then
      minelight.log("Party mode ON")
      minelight.preset("strobe")
    else
      minelight.log("Party mode OFF")
      minelight.preset("blackout")
    end
  end
end)

minelight.on("redstone.off", function(e)
  if not party then
    minelight.intensity(e.fixtureId, 0)
  end
end)

-- Command block: /ml event beat
minelight.on("custom.beat", function(e)
  if party then
    minelight.cue("main")
  end
end)

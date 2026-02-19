package xyz.srgnis.bodyhealthsystem.body.player;

import xyz.srgnis.bodyhealthsystem.body.Body;

public interface BodyProvider {
    public Body getBody();
    public void setBody(PlayerBody pb);
}

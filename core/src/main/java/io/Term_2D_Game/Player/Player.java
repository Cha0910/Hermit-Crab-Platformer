package io.Term_2D_Game.Player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;

import io.Term_2D_Game.Assets;
import io.Term_2D_Game.GameWorld;
import io.Term_2D_Game.Objects.*;

public class Player {
    public enum State {
        IDLE, WALK, JUMP, FALL, ATTACK, WALL_SLIDE, DASH, INTERACT, LOOK_UP, SPAWN, CLEARED, DEAD
    }
    private GameWorld gameWorld;
    private Body body;
    private State currentState = State.IDLE;
    private Animation<TextureRegion> currentAnim;
    private float stateTime = 0f;
    private boolean lookingRight = true;

    private static final float WIDTH = 96f;
    private static final float HEIGHT = 96f;
    private static final float PPM = 100f;

    // 이동 관련
    private Vector2 velocity;
    private static final float MOVE_SPEED = 2.0f;
    private static final float JUMP_FORCE = 4.5f;
    private final float MAX_JUMP_TIME = 0.35f;
    private boolean jumpPressed = false;
    private float jumpTime = 0f;

    // 점프 버퍼
    private float jumpBufferTimer = 0f;
    private float JUMP_BUFFER_DURATION = 0.15f;
    private boolean jumpBuffer = false;
    private float wallJumpBufferTimer = 0f;
    private float WALL_JUMP_BUFFER_DURATION = 0.1f;

    // 바닥 감지용
    private boolean isGrounded = false;
    private int groundContacts = 0;

    // 벽 타기
    private boolean isOnWall = false;
    private int leftWallContacts = 0;
    private int rightWallContacts = 0;
    private final float ON_WALL_DURATION = 2.0f;
    private float onWallTimer = 0f;
    private final float WALL_JUMP_TIME_COST = 0.7f;

    // 상호작용
    private boolean isContactFlag = false;
    private boolean isCleared = false;

    // 공격
    private boolean isAttacking = false;
    private final float ATTACK_DURATION = 0.25f;
    private float attackTimer = 0f;
    private Fixture attackSensor;

    // 대시
    private boolean isDashing = false;
    private boolean isCanDash = true;
    private final float DASH_DURATION = 2.0f; // 대시 쿨타임
    private float dashTimer = 0f;
    private final float DASH_ANIM_DURATION = 0.25f; // 대시 애니메이션 유지시간
    private float dashAnimTimer = 0f;
    private boolean dashDirectionRight = false; // 대시 방향 (좌,우 / false,true)
    private final float DASH_SPEED = 5.0f;

    // 스폰 및 리스폰
    private final float SPAWN_DURATION = 0.7f;
    private float spawnTimer = 0f;

    public Player(World world, Vector2 startPosition, GameWorld gameWorld) {
        this.gameWorld = gameWorld;
        velocity = new Vector2();
        respawn(startPosition, world);
    }

    // 바디 생성
    private void createBody(World world, Vector2 startPosition) {
        final float CAPSULE_RADIUS_PX = WIDTH / 5f;  // 캡슐 원 반지름
        final float RECT_HEIGHT_PX    = HEIGHT / 4f ;  // 캡슐 사각형 크기
        final float SENSOR_HEIGHT_PX  = HEIGHT / 12f;  // 바닥 센서 크기

        // ---- 미터 단위로 변환 ----
        final float R = CAPSULE_RADIUS_PX / PPM;
        final float H_RECT_HALF = RECT_HEIGHT_PX / 2f / PPM;
        final float H_SENSOR_HALF = SENSOR_HEIGHT_PX / 2f / PPM;

        // ---- Body 생성 ----
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set((startPosition.x + WIDTH / 2f) / PPM, (startPosition.y + HEIGHT / 2f) / PPM);
        body = world.createBody(bodyDef);
        body.setFixedRotation(true);
        body.setBullet(true);
        body.setUserData(this);

        // ---- 기본 Fixture 속성 ----
        FixtureDef bodyDefCommon = new FixtureDef();
        bodyDefCommon.density = 1f;
        bodyDefCommon.friction = 0f;
        bodyDefCommon.restitution = 0f;

        // ---- 메인 바디 구성 (캡슐형) ----
        createEdgeFixture(body, -R, H_RECT_HALF, bodyDefCommon);
        createEdgeFixture(body,  R, H_RECT_HALF, bodyDefCommon);
        createCircleFixture(body, 0,  H_RECT_HALF - R / 2, R, bodyDefCommon);
        createCircleFixture(body, 0, -(H_RECT_HALF + R / 4), R, bodyDefCommon);

        // ---- 센서 ----
        // 바닥 센서
        createSensorBox(body, (WIDTH / 6f) / PPM, H_SENSOR_HALF, 0, -(HEIGHT / 2.8f / PPM), "footSensor");

        // 좌우 벽 감지 센서
        createSensorBox(body, 2f / PPM, (HEIGHT / 5f) / PPM, -(R + 1f / PPM) , - R / 2f, "leftSensor");
        createSensorBox(body, 2f / PPM, (HEIGHT / 5f) / PPM,  (R + 1f / PPM), - R / 2f, "rightSensor");
    }


    // 좌우 엣지
    private void createEdgeFixture(Body body, float x, float hHalf, FixtureDef def) {
        EdgeShape edge = new EdgeShape();
        edge.set(new Vector2(x, -hHalf), new Vector2(x, hHalf));
        def.shape = edge;
        body.createFixture(def).setUserData(this);
        edge.dispose();
    }

    // 상하단 원
    private void createCircleFixture(Body body, float cx, float cy, float r, FixtureDef def) {
        CircleShape circle = new CircleShape();
        circle.setRadius(r);
        circle.setPosition(new Vector2(cx, cy));
        def.shape = circle;
        body.createFixture(def).setUserData(this);
        circle.dispose();
    }

    // 센서
    private void createSensorBox(Body body, float hx, float hy, float cx, float cy, String name) {
        PolygonShape sensor = new PolygonShape();
        sensor.setAsBox(hx, hy, new Vector2(cx, cy), 0);
        FixtureDef def = new FixtureDef();
        def.shape = sensor;
        def.isSensor = true;
        body.createFixture(def).setUserData(name);
        sensor.dispose();
    }

    public void update(float delta) {
        stateTime += delta;

        if(currentState == State.SPAWN){
            spawnTimer += delta;
            if(spawnTimer > SPAWN_DURATION){
                setState(State.IDLE); // 스폰 애니메이션이 끝나면 idle
            }
            else return;
        }

        if(currentState == State.DEAD){
            body.setGravityScale(0f);
            body.setLinearVelocity(0,0);
            return;
        }

        if(jumpBufferTimer > 0f){
            jumpBufferTimer -= delta;
        }

        if(wallJumpBufferTimer > 0f){
            wallJumpBufferTimer -= delta;
        }

        handleInput(delta);
        body.setGravityScale(1f); // 중력 복구

        if(isAttacking){
            attackTimer += delta;
            if(currentState != State.ATTACK){
                setState(State.ATTACK);
            }

            if(attackTimer > ATTACK_DURATION){
                isAttacking = false;
                if (attackSensor != null) {
                    body.destroyFixture(attackSensor);
                    attackSensor = null;
                }
            }
            return;
        }

        if (isGrounded) {
            if (Math.abs(velocity.x) > 0.01f) setState(State.WALK);
            else setState(State.IDLE);
            isOnWall = false;
        }
        else if(isOnWall){
            if(onWallTimer < ON_WALL_DURATION){
                onWallTimer += delta;
                //velocity.y = 0f;
                body.setGravityScale(0f); // 벽에 붙으면 중력 영향 X
                setState(State.WALL_SLIDE);
            }
            else{
                isOnWall = false;
                body.setAwake(true);
            }
        }
        else {
            if (velocity.y > 0.01f) setState(State.JUMP);
            else setState(State.FALL);
        }

        if(isDashing){
            body.setGravityScale(0f); // 대시 중 중력 제거
            dashAnimTimer += delta;
            setState(State.DASH);
            float offsetX = dashDirectionRight ? DASH_SPEED : -DASH_SPEED; // 바라보는 방향
            velocity.x = MOVE_SPEED * offsetX;
            velocity.y = 0f;
            if(dashAnimTimer > DASH_ANIM_DURATION){
                isDashing = false;
            }
        }

        if(!isCanDash){
            dashTimer += delta;
            if(dashTimer > DASH_DURATION && isGrounded){
                isCanDash = true;
            }
        }

        body.setLinearVelocity(velocity);
    }

    public void draw(SpriteBatch batch) {
        if (currentAnim == null) return;
        TextureRegion frame = currentAnim.getKeyFrame(stateTime, true);

        if (!lookingRight && !frame.isFlipX()) frame.flip(true, false);
        else if (lookingRight && frame.isFlipX()) frame.flip(true, false);

        Vector2 pos = body.getPosition();
        batch.draw(frame, pos.x * PPM - WIDTH / 2f, pos.y * PPM - HEIGHT / 2f, WIDTH, HEIGHT);
        drawWallTimerBar(batch);
    }

    // 바닥 센서 충돌 시작
    public void onBeginContact(Fixture other) {
        if (other == null) return;
        Object data = other.getUserData();

        // Box, Stone, Sand 블럭만 바닥 충돌 판정
        if (data instanceof BoxObject ||
            data instanceof StoneObject ||
            data instanceof SandObject) {

            groundContacts++;
            onWallTimer = 0f;
            isGrounded = true;
            if(jumpBufferTimer > 0f){
                jumpBufferTimer = 0f;
                jumpBuffer = true;
            }
        }

        // Flag 충돌 판정
        if(data instanceof FlagObject){
            if(!isContactFlag){
                isContactFlag = true;
            }
        }

        // Sign 충돌 판정
        if(data instanceof SignObject){
            ((SignObject) data).showMessage();
        }
    }

    // 바닥 센서 충돌 종료
    public void onEndContact(Fixture other) {
        if (other == null) return;
        Object data = other.getUserData();

        // Box, Stone, Sand 블럭만 바닥 충돌 판정
        if (data instanceof BoxObject ||
            data instanceof StoneObject ||
            data instanceof SandObject) {

            groundContacts = Math.max(0, groundContacts - 1);
            if (groundContacts == 0){
                isGrounded = false;
            }
        }

        // Flag 충돌 판정
        if(data instanceof FlagObject){
            if(isContactFlag){
                isContactFlag = false;
            }
        }

        // Sign 충돌 판정
        if(data instanceof SignObject){
            ((SignObject) data).hideMessage();
        }
    }

    // 벽 타기 충돌 검사
    public void onBeginWallContact(Fixture other, boolean isLeft) {
        if (other == null) return;
        Object data = other.getUserData();

        // 벽 타기는 Stone만 가능
        if (data instanceof StoneObject) {
            if(isLeft){
                leftWallContacts++;
            }
            else {
                rightWallContacts++;
            }
        }
    }

    // 충돌 종료
    public void onEndWallContact(Fixture other, boolean isLeft) {
        if (other == null) return;
        Object data = other.getUserData();

        // 벽 타기는 Stone만 가능
        if (data instanceof StoneObject) {
            if(isLeft){
                leftWallContacts = Math.max(0, leftWallContacts - 1);
            }
            else {
                rightWallContacts = Math.max(0,rightWallContacts - 1);
            }
            if(leftWallContacts == 0 && rightWallContacts == 0){
                isOnWall = false;
            }
        }
    }

    // 공격
    public void onBeginAttackContact(Fixture other) {
        if (other == null) return;
        Object data = other.getUserData();

        // 박스 개체는 부수기 가능
        if (data instanceof BoxObject) {
            gameWorld.addDestroyBox((BoxObject) data);
        }
    }

    public void setState(State newState) {
        if (this.currentState == newState) return;
        this.currentState = newState;
        this.stateTime = 0f;

        String animKey = "player_" + newState.name().toLowerCase();
        Animation<TextureRegion> anim = Assets.getAnimation(animKey);

        if (anim == null) {
            anim = Assets.getAnimation("player_idle"); // 애니메이션이 없으면 idle로 설정
        }

        this.currentAnim = anim;
    }

    // 입력 처리
    public void handleInput(float delta) {
        velocity.set(0, body.getLinearVelocity().y);

        boolean left = Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT);
        boolean right = Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT);

        if (left && !right) moveLeft();
        else if (right && !left) moveRight();
        else stopMovingX();

        if(isOnWall){
            boolean up = Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP);
            boolean down = Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN);

            if (up && !down) moveUp();
            else if (down && !up) moveDown();
            else stopMovingY();
        }

        // 점프 처리
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || jumpBuffer) {
            jump();
            jumpBuffer = false;
        }

        // 점프 길이 조절
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE) && jumpPressed) {
            controlJumpHeight(delta);
        }

        if (!Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
            jumpPressed = false;
        }

        // 상호 작용
        if(Gdx.input.isKeyPressed(Input.Keys.E) || Gdx.input.isKeyPressed(Input.Keys.C)){
            interact();
        }

        // 공격
        if(Gdx.input.isKeyJustPressed(Input.Keys.Z) || Gdx.input.isButtonJustPressed(0)){
            attack();
        }

        // 대시
        if(Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)){
            dash();
        }
    }

    // 리스폰
    public void respawn(Vector2 startPos, World world) {
        createBody(world, startPos);

        body.setLinearVelocity(MOVE_SPEED / 1.5f, 0);
        body.setAngularVelocity(0);
        //body.setGravityScale(0f); // 스폰 중에는 중력 영향 X
        lookingRight = true;
        isGrounded = false;
        groundContacts = 0;
        spawnTimer = 0f;
        dashTimer = 0f;
        isCanDash = true;
        setState(State.SPAWN);
    }

    // 이동 관련 함수들
    // 공격 중엔 벽타기 불가
    public void moveLeft() {
        if(dashDirectionRight) isDashing = false; // 대시 중 반대 방향키 입력으로 캔슬 가능
        velocity.x = -MOVE_SPEED;
        lookingRight = false;

        if(isOnWall && onWallTimer < ON_WALL_DURATION){
            wallJumpBufferTimer = WALL_JUMP_BUFFER_DURATION;
        }

        isOnWall = leftWallContacts > 0 && onWallTimer < ON_WALL_DURATION && !isAttacking;
    }

    public void moveRight() {
        if(!dashDirectionRight) isDashing = false; // 대시 중 반대 방향키 입력으로 캔슬 가능
        velocity.x = MOVE_SPEED;
        lookingRight = true;

        if(isOnWall && onWallTimer < ON_WALL_DURATION){
            wallJumpBufferTimer = WALL_JUMP_BUFFER_DURATION;
        }

        isOnWall = rightWallContacts > 0 && onWallTimer < ON_WALL_DURATION && !isAttacking;
    }

    public void stopMovingX() {
        velocity.x = 0;
    }
    public void moveUp() {
        velocity.y = MOVE_SPEED * 0.5f;
    }

    public void moveDown() {
        velocity.y = -MOVE_SPEED * 0.5f;
    }

    public void stopMovingY() {
        velocity.y = 0;
    }
    public void jump() {
        if (!isGrounded) {  // 공중에 떠있고
            if(isOnWall || wallJumpBufferTimer > 0f){   // 벽을 타고 있는 상태면
                wallJump(); // 벽점프
            }
            else if(isDashing) return; // 대시 중 점프 불가

            jumpBufferTimer = JUMP_BUFFER_DURATION;
            return; // 공중에 떠있거나 대시 중이면 점프 X
        }
        jumpPressed = true;
        jumpTime = 0f;
        velocity.y = JUMP_FORCE;
        isGrounded = false;
    }

    public void controlJumpHeight(float delta){
        if (jumpTime < MAX_JUMP_TIME) {
            velocity.y = JUMP_FORCE;
            jumpTime += delta;
        }
    }

    private void wallJump(){
        /*
        if(isOnWall){ // 제자리 점프 막기
            return;   // 작동은 하지만 로직이 좋지 않은듯
        }
        */
        wallJumpBufferTimer = 0f;
        jumpPressed = true;
        jumpTime = 0f;
        onWallTimer += WALL_JUMP_TIME_COST;
        isGrounded = false;
        isOnWall = false;
    }

    public void dash(){
        if(isDashing || !isCanDash || isOnWall) return;
        isDashing = true;
        isCanDash = false;
        dashTimer = 0f;
        dashAnimTimer = 0f;

        dashDirectionRight = lookingRight;

        Vector2 pos = new Vector2(body.getPosition().x * 100f, body.getPosition().y * 100f);
        gameWorld.addEffect(new DashEffect(pos, 0.3f, lookingRight));
    }

    // 상호작용
    public void interact(){
        if(isContactFlag && isGrounded){
            isCleared = true;
            velocity.x = 0;
            body.setLinearVelocity(0, 0);
            setState(State.CLEARED);
        }
    }

    // 공격
    public void attack(){
        if(isAttacking || isOnWall) return;
        isDashing = false; // 대시 중 공격으로 캔슬 가능
        isAttacking = true;
        attackTimer = 0f;

        PolygonShape attackShape = new PolygonShape();
        float offsetX = lookingRight ? 0.5f : -0.5f; // 바라보는 방향
        attackShape.setAsBox(0.3f, 0.3f, new Vector2(offsetX, -0.1f), 0);

        FixtureDef def = new FixtureDef();
        def.shape = attackShape;
        def.isSensor = true;

        attackSensor = body.createFixture(def);
        attackSensor.setUserData("attackSensor");

        attackShape.dispose();

        Vector2 pos = new Vector2(body.getPosition().x * 100f, body.getPosition().y * 100f);
        gameWorld.addEffect(new AttackEffect(pos, ATTACK_DURATION, lookingRight));
    }

    // 벽타기 스태미너 시각화
    private void drawWallTimerBar(SpriteBatch batch) {
        if (onWallTimer == 0 || onWallTimer > ON_WALL_DURATION) return;

        float ratio = 1f - (onWallTimer / ON_WALL_DURATION);
        ratio = Math.max(0f, Math.min(1f, ratio));

        Vector2 pos = body.getPosition();
        float barWidth = WIDTH;
        float barHeight = 4f;

        float barX = pos.x * PPM - WIDTH / 2f;
        float barY = pos.y * PPM + HEIGHT / 2f + 5f;

        batch.setColor(0.2f, 0.2f, 0.2f, 1f);
        batch.draw(Assets.whitePixel, barX, barY, barWidth, barHeight);

        float r = 1f - ratio;
        float g = ratio;
        batch.setColor(r, g, 0f, 1f);
        batch.draw(Assets.whitePixel, barX, barY, barWidth * ratio, barHeight);

        batch.setColor(1f, 1f, 1f, 1f);
    }

    public Body getBody() {
        return body;
    }

    public boolean isGrounded() {
        return isGrounded;
    }

    public State getState() {
        return currentState;
    }

    public Boolean getIsCleared(){
        return this.isCleared;
    }

    public void setIsCleared(boolean isCleared){
        this.isCleared = isCleared;
    }

    public void addStateTime(float delta){
        stateTime += delta;
    }
}

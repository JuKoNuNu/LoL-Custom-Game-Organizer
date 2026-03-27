# EC2 배포 가이드

## 환경변수 설정

```bash
export RIOT_API_KEY=RGAPI-3387f588-74af-401d-822b-d606ea95b702
export RIOT_ID_TOKEN='your_riot_id_token_here'
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL=jdbc:mysql://lol.cha8u482s4nu.ap-southeast-2.rds.amazonaws.com:3306/lol
export SPRING_DATASOURCE_USERNAME=admin
export SPRING_DATASOURCE_PASSWORD='wnguddl8!'
```

### 환경변수 영구 설정 (재접속해도 유지)

```bash
cat >> ~/.bashrc << 'EOF'
export RIOT_API_KEY=RGAPI-3387f588-74af-401d-822b-d606ea95b702
export RIOT_ID_TOKEN='your_riot_id_token_here'
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL=jdbc:mysql://lol.cha8u482s4nu.ap-southeast-2.rds.amazonaws.com:3306/lol
export SPRING_DATASOURCE_USERNAME=admin
export SPRING_DATASOURCE_PASSWORD='wnguddl8!'
EOF
source ~/.bashrc
```

### RIOT_ID_TOKEN 갱신 방법
1. 브라우저에서 `https://auth.riotgames.com` 로그인
2. 개발자 도구(F12) > Application > Cookies에서 `id_token` 값 복사
3. `export RIOT_ID_TOKEN='복사한값'` 후 앱 재시작


## 빌드

```bash
cd ~/LoL-Custom-Game-Organizer
./gradlew bootJar
```

## 실행

```bash
nohup java -jar build/libs/app.jar > app.log 2>&1 &
```

## 재시작 (기존 프로세스 종료 후 실행)

```bash
ps -ef | grep app.jar | grep -v grep | awk '{print $2}' | xargs kill -9 2>/dev/null
nohup java -jar build/libs/app.jar > app.log 2>&1 &
```

## 로그 확인

```bash
# 실시간 로그
tail -f app.log

# 최근 로그 20줄
tail -20 app.log
```

## RDS 직접 접속 (디버깅용)

```bash
mysql -h lol.cha8u482s4nu.ap-southeast-2.rds.amazonaws.com -P 3306 -u admin -p'wnguddl8!'
```

## 참고

| 항목 | 값 |
|------|-----|
| EC2 유저 | ec2-user |
| Java | Corretto 17 |
| 앱 경로 | ~/LoL-Custom-Game-Organizer |
| JAR 파일 | build/libs/app.jar |
| 앱 포트 | 5000 |
| RDS 엔진 | MySQL (db.t4g.micro) |
| RDS 리전 | ap-southeast-2 |
